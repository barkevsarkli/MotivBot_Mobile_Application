import Foundation

// MARK: - API Contract
//
// The MotivBot unified pipeline on Hugging Face Spaces (Gradio):
//   1. POST {base}/gradio_api/call/run_pipeline   body {"data": [text, age, gender, phq9, rses]}
//      -> {"event_id": "..."}
//   2. GET  {base}/gradio_api/call/run_pipeline/{event_id}
//      -> server-sent events; "event: complete" carries data = [markdown summary, structured JSON]
// Scoring follows how_to_score.docx; the scores are computed in the app.

struct PredictionRequest: Equatable {
    /// Raw values are the pipeline's gender choices.
    enum Gender: String {
        case male = "Erkek"
        case female = "Kadın"
        /// "Belirtmek istemiyorum". The pipeline has no such choice; it is sent as Non-binary.
        case unspecified = "Non-binary"

        /// Numeric encoding: male = 0, prefer not to say = 0.5, female = 1.
        var numericValue: Double {
            switch self {
            case .male: return 0
            case .unspecified: return 0.5
            case .female: return 1
            }
        }
    }

    let text: String
    let age: Int
    let gender: Gender
    /// PHQ-9 items 1-9 (0-3 each). The attention check is never included.
    let phq9Items: [Int]
    /// Rosenberg items 1-10 as raw app values (Strongly agree = 3 ... Strongly disagree = 0).
    let rsesItems: [Int]

    /// PHQ-9 total, 0-27: the sum of the nine items.
    var phq9Score: Int { phq9Items.reduce(0, +) }

    /// RSES total, 0-30 (how_to_score.docx): normal items score the raw value, reverse items score 3 - value.
    var rsesScore: Int {
        rsesItems.enumerated().reduce(0) { total, item in
            let isReverse = PredictionRequest.rsesReverseItems.contains(item.offset + 1)
            return total + (isReverse ? 3 - item.element : item.element)
        }
    }

    /// PHQ-9 item 9 asks about thoughts of self-harm.
    var selfHarmItem: Int { phq9Items.count == 9 ? phq9Items[8] : 0 }

    /// 1-based item numbers of the reverse-scored Rosenberg statements, in the order of qd_1 ... qd_10.
    static let rsesReverseItems: Set<Int> = [3, 5, 8, 9, 10]
}

extension PredictionRequest: Encodable {
    private enum CodingKeys: String, CodingKey { case data }

    /// `{"data": [text, age, gender, phq9, rses]}`. The order matters.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        var data = container.nestedUnkeyedContainer(forKey: .data)
        try data.encode(text)
        try data.encode(age)
        try data.encode(gender.rawValue)
        try data.encode(phq9Score)
        try data.encode(rsesScore)
    }
}

/// The structured JSON in `data[1]` of the pipeline result.
struct PredictionResponse: Codable, Equatable {
    struct Message: Codable, Equatable, Hashable {
        let text: String
        let category: String
        let score: Double
    }

    enum Level: String, Codable, CaseIterable {
        case no = "No"
        case low = "Low"
        case high = "High"
    }

    /// Independent probabilities (0-1) keyed by `WellBeingDimension.rawValue`.
    let emotions: [String: Double]
    let messages: [Message]

    func probability(for dimension: WellBeingDimension) -> Double? {
        emotions[dimension.rawValue]
    }

    /// Scores above 0.5 mean the emotion is likely present (per the API documentation).
    static let presentThreshold = 0.5
    static let lowThreshold = 0.25

    func level(for dimension: WellBeingDimension) -> Level? {
        guard let probability = probability(for: dimension) else { return nil }
        if probability >= Self.presentThreshold { return .high }
        if probability >= Self.lowThreshold { return .low }
        return .no
    }
}

// MARK: - Errors

enum MotivBotAPIError: Error, Equatable {
    case serverUnreachable
    /// The Space is asleep or rate limited (503 / 429). Retry after a pause.
    case modelLoading
    case invalidInput(String)
    case decoding
    case http(Int)
}

// MARK: - Service

protocol PredictionServicing {
    func predict(_ request: PredictionRequest) async throws -> PredictionResponse
}

final class MotivBotAPIService: PredictionServicing {

    static let defaultBaseURL = URL(string: "https://flightmaestro-motivbot-pipeline.hf.space")!
    static let endpointPath = "gradio_api/call/run_pipeline"

    /// Reads `MotivBotAPIBaseURL` from Info.plist (set via the `MOTIVBOT_API_BASE_URL` build setting).
    static var configuredBaseURL: URL {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "MotivBotAPIBaseURL") as? String,
              let url = URL(string: raw.trimmingCharacters(in: .whitespaces)),
              url.scheme != nil, url.host != nil else {
            return defaultBaseURL
        }
        return url
    }

    private let baseURL: URL
    private let session: URLSession

    init(baseURL: URL = MotivBotAPIService.configuredBaseURL, session: URLSession? = nil) {
        self.baseURL = baseURL
        if let session {
            self.session = session
        } else {
            // A cold start of the Space can take up to a minute.
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = 120
            configuration.timeoutIntervalForResource = 180
            configuration.waitsForConnectivity = false
            self.session = URLSession(configuration: configuration)
        }
    }

    func predict(_ body: PredictionRequest) async throws -> PredictionResponse {
        let endpoint = baseURL.appendingPathComponent(Self.endpointPath)

        var submit = URLRequest(url: endpoint)
        submit.httpMethod = "POST"
        submit.setValue("application/json", forHTTPHeaderField: "Content-Type")
        submit.timeoutInterval = 90
        submit.httpBody = try JSONEncoder().encode(body)
        let submitData = try await send(submit)

        struct EventID: Decodable { let event_id: String }
        guard let eventID = try? JSONDecoder().decode(EventID.self, from: submitData).event_id else {
            throw MotivBotAPIError.decoding
        }

        var result = URLRequest(url: endpoint.appendingPathComponent(eventID))
        result.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        result.timeoutInterval = 120
        let stream = try await send(result)
        return try Self.parseEventStream(stream)
    }

    /// Reads the Gradio server-sent events and decodes the structured JSON of the `complete` event.
    static func parseEventStream(_ data: Data) throws -> PredictionResponse {
        guard let text = String(data: data, encoding: .utf8) else { throw MotivBotAPIError.decoding }

        var event = ""
        for line in text.components(separatedBy: .newlines) {
            if line.hasPrefix("event:") {
                event = line.dropFirst("event:".count).trimmingCharacters(in: .whitespaces)
            } else if line.hasPrefix("data:") {
                let payload = Data(line.dropFirst("data:".count).trimmingCharacters(in: .whitespaces).utf8)
                switch event {
                case "complete":
                    return try decodeResult(payload)
                case "error":
                    struct ErrorBody: Decodable { let error: String? }
                    let message = (try? JSONDecoder().decode(ErrorBody.self, from: payload))?.error
                    throw MotivBotAPIError.invalidInput(message ?? "")
                default:
                    continue
                }
            }
        }
        throw MotivBotAPIError.decoding
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

    private func send(_ request: URLRequest) async throws -> Data {
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw MotivBotAPIError.serverUnreachable
        }
        guard let http = response as? HTTPURLResponse else {
            throw MotivBotAPIError.serverUnreachable
        }
        switch http.statusCode {
        case 200..<300:
            return data
        case 429, 503:
            throw MotivBotAPIError.modelLoading
        case 400, 422:
            throw MotivBotAPIError.invalidInput(String(data: data, encoding: .utf8) ?? "")
        default:
            throw MotivBotAPIError.http(http.statusCode)
        }
    }
}
