import Foundation

// MARK: - API Contract
//
// The MotivBot unified pipeline on Hugging Face Spaces (Gradio), named-parameter API:
//   1. POST {base}/gradio_api/call/v2/run_pipeline
//      body {"text": ..., "age": ..., "gender": ..., "phq9": ..., "rses": ...} -> {"event_id": "..."}
//   2. GET  {base}/gradio_api/call/run_pipeline/{event_id}
//      -> server-sent events; "event: complete" carries data = [markdown summary, structured JSON]
// Scoring follows how_to_score.docx; the scores are computed in the app.
// The Space detects the language of the text and answers in it (see `PipelineLanguage`).

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

// MARK: - Validation

extension PredictionRequest {
    /// Allowed length of the trimmed emotion text, in characters.
    static let textLengthRange = 10...2000
    static let itemRange = 0...3

    /// Checks the inputs before anything is sent. Throws `MotivBotAPIError.invalidInput`.
    func validate() throws {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard Self.textLengthRange.contains(trimmed.count) else {
            throw MotivBotAPIError.invalidInput("text length")
        }
        guard PredictionRequestBuilder.ageRange.contains(age) else {
            throw MotivBotAPIError.invalidInput("age")
        }
        guard phq9Items.count == 9, phq9Items.allSatisfy(Self.itemRange.contains) else {
            throw MotivBotAPIError.invalidInput("phq9 items")
        }
        guard rsesItems.count == 10, rsesItems.allSatisfy(Self.itemRange.contains) else {
            throw MotivBotAPIError.invalidInput("rses items")
        }
    }
}

extension PredictionRequest: Encodable {
    private enum CodingKeys: String, CodingKey { case text, age, gender, phq9, rses }

    /// `{"text": ..., "age": ..., "gender": ..., "phq9": ..., "rses": ...}` for the v2 endpoint.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(text, forKey: .text)
        try container.encode(age, forKey: .age)
        try container.encode(gender.rawValue, forKey: .gender)
        try container.encode(phq9Score, forKey: .phq9)
        try container.encode(rsesScore, forKey: .rses)
    }
}

/// The structured JSON in `data[1]` of the pipeline result.
struct PredictionResponse: Codable, Equatable {
    struct Message: Codable, Equatable, Hashable {
        let text: String
        let category: String
        let score: Double
        /// The quote's author, when the pipeline knows it.
        let author: String?
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

/// What the user is told when an analysis fails. Each case has one localized message.
enum AnalysisError: Error, Equatable {
    case offline
    case serviceWakingUp
    case serviceBusy
    case unexpected

    var messageKey: String {
        switch self {
        case .offline: return "error_server_unreachable"
        case .serviceWakingUp: return "error_model_loading"
        case .serviceBusy: return "error_service_busy"
        case .unexpected: return "error_generic"
        }
    }
}

/// Raw failures of the pipeline client. Only `analysisError` is shown to the user.
enum MotivBotAPIError: Error, Equatable {
    /// The device has no network connection.
    case offline
    /// A network failure such as a timeout or a dropped connection.
    case network(URLError.Code)
    /// 502 / 503 / 504: the Space is asleep or restarting.
    case spaceStarting
    /// 429: too many requests.
    case rateLimited
    /// The Space's ZeroGPU quota is used up. Retrying soon does not help.
    case quotaExceeded
    /// The inputs were rejected, by `PredictionRequest.validate()` or by the pipeline.
    case invalidInput(String)
    /// The event stream closed without a result.
    case streamEnded
    case decoding
    case http(Int)
    /// The prediction took longer than `RetryPolicy.deadline`.
    case deadlineExceeded

    static let transientNetworkCodes: Set<URLError.Code> = [
        .timedOut, .networkConnectionLost, .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed,
    ]

    var isRetryable: Bool {
        switch self {
        case .network(let code): return Self.transientNetworkCodes.contains(code)
        case .spaceStarting, .rateLimited, .streamEnded: return true
        case .offline, .quotaExceeded, .invalidInput, .decoding, .http, .deadlineExceeded: return false
        }
    }

    var analysisError: AnalysisError {
        switch self {
        case .offline, .network: return .offline
        case .spaceStarting, .deadlineExceeded: return .serviceWakingUp
        case .rateLimited, .quotaExceeded: return .serviceBusy
        case .invalidInput, .streamEnded, .decoding, .http: return .unexpected
        }
    }
}

// MARK: - Service

enum PredictionProgress: Equatable {
    case connecting
    /// The Space answered 503 and is starting; the request is retried.
    case wakingUp
    /// The job is queued or running.
    case analysing
}

protocol PredictionServicing {
    /// Runs the pipeline, retrying while the Space wakes up. Throws `MotivBotAPIError` or `CancellationError`.
    func predict(_ request: PredictionRequest, progress: @escaping (PredictionProgress) -> Void) async throws -> PredictionResponse
    /// Pings the Space so it is awake by the time the user submits. Never throws; at most one ping runs at a time.
    func prewarm() async
}

final class MotivBotAPIService: PredictionServicing {

    static let defaultBaseURL = URL(string: "https://flightmaestro-motivbot-pipeline.hf.space")!
    static let submitPath = "gradio_api/call/v2/run_pipeline"
    static let resultPath = "gradio_api/call/run_pipeline"
    static let infoPath = "gradio_api/info"

    /// Reads `MotivBotAPIBaseURL` from Info.plist (set via the `MOTIVBOT_API_BASE_URL` build setting).
    static var configuredBaseURL: URL {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "MotivBotAPIBaseURL") as? String,
              let url = URL(string: raw.trimmingCharacters(in: .whitespaces)),
              url.scheme != nil, url.host != nil else {
            return defaultBaseURL
        }
        return url
    }

    struct RetryPolicy {
        var initialDelay: Duration = .seconds(1)
        var maxDelay: Duration = .seconds(16)
        /// No retry starts once this much time has passed since the first attempt.
        var budget: Duration = .seconds(90)
        /// Hard limit for one prediction, retries and streaming included.
        var deadline: Duration = .seconds(150)
        /// Per-request timeout. The event stream sends a heartbeat well within it.
        var requestTimeout: TimeInterval = 30
        /// A cold Space can take about a minute to answer the warm-up ping.
        var prewarmTimeout: TimeInterval = 90

        /// Exponential backoff (1 s, 2 s, 4 s ... capped at `maxDelay`) scaled by 0.75-1.25 for jitter.
        /// `attempt` is 1-based; `random` is in 0..<1.
        func delay(afterAttempt attempt: Int, random: Double) -> Duration {
            let exponential = initialDelay * (1 << min(attempt - 1, 16))
            let base = min(exponential, maxDelay)
            return base * (0.75 + 0.5 * random)
        }
    }

    /// The time source for retries, replaceable in tests.
    struct Timing {
        var now: () -> ContinuousClock.Instant
        var sleep: (Duration) async throws -> Void
        /// Uniform in 0..<1.
        var random: () -> Double

        static let live = Timing(
            now: { ContinuousClock.now },
            sleep: { try await Task.sleep(for: $0) },
            random: { Double.random(in: 0..<1) }
        )
    }

    private let baseURL: URL
    private let transport: HTTPTransport
    private let policy: RetryPolicy
    private let timing: Timing
    private var prewarmTask: Task<Void, Never>?

    init(
        baseURL: URL = MotivBotAPIService.configuredBaseURL,
        transport: HTTPTransport? = nil,
        policy: RetryPolicy = RetryPolicy(),
        timing: Timing = .live
    ) {
        self.baseURL = baseURL
        self.policy = policy
        self.timing = timing
        if let transport {
            self.transport = transport
        } else {
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = policy.requestTimeout
            configuration.timeoutIntervalForResource = policy.deadline.timeInterval
            configuration.waitsForConnectivity = false
            // Results are sensitive; keep them out of the on-disk cache.
            configuration.urlCache = nil
            configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
            self.transport = URLSessionTransport(session: URLSession(configuration: configuration))
        }
    }

    func prewarm() async {
        if let prewarmTask {
            return await prewarmTask.value
        }
        var request = URLRequest(url: baseURL.appendingPathComponent(Self.infoPath))
        request.timeoutInterval = policy.prewarmTimeout
        let transport = self.transport
        let task = Task { _ = try? await transport.data(for: request) }
        prewarmTask = task
        await task.value
        prewarmTask = nil
    }

    func predict(_ request: PredictionRequest, progress: @escaping (PredictionProgress) -> Void) async throws -> PredictionResponse {
        try request.validate()
        let start = timing.now()
        progress(.connecting)

        var attempt = 0
        while true {
            attempt += 1
            try Task.checkCancellation()
            do {
                return try await runOnce(request, start: start, progress: progress)
            } catch let error as MotivBotAPIError where error.isRetryable {
                let delay = policy.delay(afterAttempt: attempt, random: timing.random())
                guard timing.now() - start + delay <= policy.budget else { throw error }
                if error == .spaceStarting { progress(.wakingUp) }
                try await timing.sleep(delay)
            }
        }
    }

    /// One submit + stream round trip.
    private func runOnce(
        _ body: PredictionRequest,
        start: ContinuousClock.Instant,
        progress: (PredictionProgress) -> Void
    ) async throws -> PredictionResponse {
        do {
            var submit = URLRequest(url: baseURL.appendingPathComponent(Self.submitPath))
            submit.httpMethod = "POST"
            submit.setValue("application/json", forHTTPHeaderField: "Content-Type")
            submit.timeoutInterval = policy.requestTimeout
            submit.httpBody = try JSONEncoder().encode(body)
            let (submitData, submitResponse) = try await transport.data(for: submit)
            try Self.check(submitResponse, body: submitData)

            struct EventID: Decodable { let event_id: String }
            guard let eventID = try? JSONDecoder().decode(EventID.self, from: submitData).event_id else {
                throw MotivBotAPIError.decoding
            }
            progress(.analysing)

            var result = URLRequest(url: baseURL.appendingPathComponent(Self.resultPath).appendingPathComponent(eventID))
            result.setValue("text/event-stream", forHTTPHeaderField: "Accept")
            result.timeoutInterval = policy.requestTimeout
            let (streamResponse, stream) = try await transport.lines(for: result)
            try Self.check(streamResponse, body: nil)
            var lines = stream

            var reader = GradioResultReader()
            while let line = try await lines.next() {
                if timing.now() - start > policy.deadline { throw MotivBotAPIError.deadlineExceeded }
                if let response = try reader.consume(line) { return response }
            }
            return try reader.finish()
        } catch {
            throw Self.mapTransportError(error)
        }
    }

    static func check(_ response: HTTPURLResponse, body: Data?) throws {
        switch response.statusCode {
        case 200..<300:
            return
        case 429:
            throw MotivBotAPIError.rateLimited
        case 502, 503, 504:
            throw MotivBotAPIError.spaceStarting
        case 400..<500:
            throw MotivBotAPIError.invalidInput(body.flatMap { String(data: $0, encoding: .utf8) } ?? "")
        default:
            throw MotivBotAPIError.http(response.statusCode)
        }
    }

    /// Passes `MotivBotAPIError` and cancellation through and maps everything else.
    static func mapTransportError(_ error: Error) -> Error {
        if error is MotivBotAPIError || error is CancellationError { return error }
        if let urlError = error as? URLError {
            switch urlError.code {
            case .cancelled: return CancellationError()
            case .notConnectedToInternet, .dataNotAllowed, .internationalRoamingOff: return MotivBotAPIError.offline
            default: return MotivBotAPIError.network(urlError.code)
            }
        }
        return MotivBotAPIError.network(.unknown)
    }

    /// Reads a whole recorded event stream. Used for fixtures; the live path reads line by line.
    static func parseEventStream(_ text: String) throws -> PredictionResponse {
        var reader = GradioResultReader()
        var splitter = LineSplitter()
        for byte in text.utf8 {
            if let line = splitter.append(byte), let response = try reader.consume(line) {
                return response
            }
        }
        if let line = splitter.finish(), let response = try reader.consume(line) {
            return response
        }
        return try reader.finish()
    }
}

private extension Duration {
    var timeInterval: TimeInterval {
        let (seconds, attoseconds) = components
        return TimeInterval(seconds) + TimeInterval(attoseconds) / 1e18
    }
}
