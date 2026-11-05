import Foundation

// MARK: - Transport

/// The HTTP layer of `MotivBotAPIService`, replaceable in tests.
protocol HTTPTransport {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
    /// Opens the response body as a sequence of lines.
    func lines(for request: URLRequest) async throws -> (HTTPURLResponse, any LineStream)
}

/// Lines of a response body, empty lines included (they end server-sent events).
nonisolated protocol LineStream {
    /// The next line without its terminator, or nil at the end of the body.
    mutating func next() async throws -> String?
}

struct URLSessionTransport: HTTPTransport {
    let session: URLSession

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (data, http)
    }

    func lines(for request: URLRequest) async throws -> (HTTPURLResponse, any LineStream) {
        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        return (http, ByteLineStream(bytes: bytes.makeAsyncIterator()))
    }
}

/// Splits a byte stream into lines. `URLSession.AsyncBytes.lines` drops empty lines, which SSE needs.
nonisolated struct ByteLineStream<Bytes: AsyncIteratorProtocol>: LineStream where Bytes.Element == UInt8 {
    var bytes: Bytes
    private var splitter = LineSplitter()
    private var finished = false

    init(bytes: Bytes) {
        self.bytes = bytes
    }

    mutating func next() async throws -> String? {
        while !finished {
            guard let byte = try await bytes.next() else {
                finished = true
                return splitter.finish()
            }
            if let line = splitter.append(byte) { return line }
        }
        return nil
    }
}

/// Collects UTF-8 bytes into lines ended by `\n` or `\r\n`.
nonisolated struct LineSplitter {
    private var buffer: [UInt8] = []

    /// Returns a line when `byte` ends one.
    mutating func append(_ byte: UInt8) -> String? {
        guard byte == UInt8(ascii: "\n") else {
            buffer.append(byte)
            return nil
        }
        return takeLine()
    }

    /// Returns the last line if the body did not end with a newline.
    mutating func finish() -> String? {
        buffer.isEmpty ? nil : takeLine()
    }

    private mutating func takeLine() -> String {
        if buffer.last == UInt8(ascii: "\r") { buffer.removeLast() }
        let line = String(decoding: buffer, as: UTF8.self)
        buffer.removeAll(keepingCapacity: true)
        return line
    }
}

// MARK: - Server-sent events

struct ServerSentEvent: Equatable {
    var event: String
    var data: String
}

/// Builds events from lines: `event:` and `data:` fields, `:` comments, and multi-line `data`
/// joined with newlines. An empty line ends the event.
struct ServerSentEventParser {
    private var event = ""
    private var dataLines: [String] = []

    /// Returns an event when `line` completes one.
    mutating func consume(_ line: String) -> ServerSentEvent? {
        if line.isEmpty { return dispatch() }
        if line.hasPrefix(":") { return nil }

        let field: Substring
        var value: Substring
        if let colon = line.firstIndex(of: ":") {
            field = line[..<colon]
            value = line[line.index(after: colon)...]
            if value.first == " " { value = value.dropFirst() }
        } else {
            field = line[...]
            value = ""
        }

        switch field {
        case "event": event = String(value)
        case "data": dataLines.append(String(value))
        default: break
        }
        return nil
    }

    /// Returns an event that was not followed by an empty line before the stream ended.
    mutating func finish() -> ServerSentEvent? {
        dispatch()
    }

    private mutating func dispatch() -> ServerSentEvent? {
        defer {
            event = ""
            dataLines = []
        }
        guard !event.isEmpty || !dataLines.isEmpty else { return nil }
        return ServerSentEvent(event: event.isEmpty ? "message" : event, data: dataLines.joined(separator: "\n"))
    }
}

// MARK: - Gradio result

/// Reads a Gradio `/call` stream: `heartbeat` and `generating` are skipped, `complete` carries the result,
/// `error` fails the call.
struct GradioResultReader {
    private var parser = ServerSentEventParser()

    /// Returns the result once the `complete` event has arrived.
    mutating func consume(_ line: String) throws -> PredictionResponse? {
        guard let event = parser.consume(line) else { return nil }
        return try Self.handle(event)
    }

    /// Call at the end of the body. Throws `.streamEnded` if no result arrived.
    mutating func finish() throws -> PredictionResponse {
        if let event = parser.finish(), let response = try Self.handle(event) {
            return response
        }
        throw MotivBotAPIError.streamEnded
    }

    private static func handle(_ event: ServerSentEvent) throws -> PredictionResponse? {
        switch event.event {
        case "complete":
            return try decodeResult(Data(event.data.utf8))
        case "error":
            throw classifyError(Data(event.data.utf8))
        default:
            return nil
        }
    }

    /// Error payloads seen from the Space: `null`, a plain string such as `"404: Not Found"`,
    /// or `{"error": "...", "title": "..."}`.
    static func classifyError(_ payload: Data) -> MotivBotAPIError {
        let object = try? JSONSerialization.jsonObject(with: payload, options: .fragmentsAllowed)
        if let message = object as? String {
            return message.hasPrefix("404") ? .http(404) : .invalidInput(message)
        }
        guard let body = object as? [String: Any] else { return .invalidInput("") }
        let message = body["error"] as? String ?? ""
        let title = body["title"] as? String ?? ""
        if (title + " " + message).localizedCaseInsensitiveContains("quota") {
            return .quotaExceeded
        }
        return .invalidInput(message)
    }

    /// `data` is `[markdown, structured]`. The structured part may arrive as an object or as an escaped JSON string.
    private static func decodeResult(_ payload: Data) throws -> PredictionResponse {
        guard let array = try? JSONSerialization.jsonObject(with: payload) as? [Any], array.count >= 2 else {
            throw MotivBotAPIError.decoding
        }
        let structured: Data
        if let string = array[1] as? String {
            structured = Data(string.utf8)
        } else if JSONSerialization.isValidJSONObject(array[1]),
                  let object = try? JSONSerialization.data(withJSONObject: array[1]) {
            structured = object
        } else {
            throw MotivBotAPIError.decoding
        }
        do {
            return try JSONDecoder().decode(PredictionResponse.self, from: structured)
        } catch {
            throw MotivBotAPIError.decoding
        }
    }
}
