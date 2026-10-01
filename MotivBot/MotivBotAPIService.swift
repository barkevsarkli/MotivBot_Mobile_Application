import Foundation

// MARK: - API Contract
//
// Mirrors the platform-neutral contract of motivbot-inference (POST /predict, GET /health).
// The app sends raw answers; the server computes all scores.

struct PredictionRequest: Codable, Equatable {
    enum Gender: String, Codable {
        case male
        case female
        case unspecified
    }

    let text: String
    let age: Int
    let gender: Gender
    /// PHQ-9 items 1-9 (0-3 each). The attention check is never included.
    let phq9Items: [Int]
    /// Rosenberg items 1-10 as raw app values (Strongly agree = 3 ... Strongly disagree = 0).
    let rsesItems: [Int]
    let language: String?

    enum CodingKeys: String, CodingKey {
        case text, age, gender, language
        case phq9Items = "phq9_items"
        case rsesItems = "rses_items"
    }
}

struct PredictionResponse: Codable, Equatable {
    struct InputsUsed: Codable, Equatable {
        let age: Int
        let sex: Double
        let phq9: Int
        let rosenberg: Int

        enum CodingKeys: String, CodingKey {
            case age = "Age"
            case sex
            case phq9 = "PHQ-9"
            case rosenberg = "Rosenberg.self-esteem"
        }
    }

    enum Level: String, Codable, CaseIterable {
        case no = "No"
        case low = "Low"
        case high = "High"
    }

    struct Probabilities: Codable, Equatable {
        let no: Double
        let low: Double
        let high: Double

        enum CodingKeys: String, CodingKey {
            case no = "No"
            case low = "Low"
            case high = "High"
        }
    }

    struct Dimension: Codable, Equatable, Identifiable {
        let key: String
        let level: Level
        let probabilities: Probabilities

        var id: String { key }
    }

    let modelId: String
    let inputsUsed: InputsUsed
    let dimensions: [Dimension]
    let elevatedRisk: Bool

    enum CodingKeys: String, CodingKey {
        case modelId = "model_id"
        case inputsUsed = "inputs_used"
        case dimensions
        case elevatedRisk = "elevated_risk"
    }
}

enum HealthStatus: String, Decodable {
    case loading
    case ready
    case error
}

// MARK: - Errors

enum MotivBotAPIError: Error, Equatable {
    case serverUnreachable
    case modelLoading
    case invalidInput(String)
    case decoding
    case http(Int)
}

// MARK: - Service

protocol PredictionServicing {
    func health() async throws -> HealthStatus
    func predict(_ request: PredictionRequest) async throws -> PredictionResponse
}

final class MotivBotAPIService: PredictionServicing {

    static let defaultBaseURL = URL(string: "http://localhost:8000")!

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
            // The first request after the model loads can be slow on CPU.
            let configuration = URLSessionConfiguration.default
            configuration.timeoutIntervalForRequest = 120
            configuration.timeoutIntervalForResource = 120
            configuration.waitsForConnectivity = false
            self.session = URLSession(configuration: configuration)
        }
    }

    func health() async throws -> HealthStatus {
        var request = URLRequest(url: baseURL.appendingPathComponent("health"))
        request.timeoutInterval = 10
        let data = try await send(request)
        struct Body: Decodable { let status: HealthStatus }
        do {
            return try JSONDecoder().decode(Body.self, from: data).status
        } catch {
            throw MotivBotAPIError.decoding
        }
    }

    func predict(_ body: PredictionRequest) async throws -> PredictionResponse {
        var request = URLRequest(url: baseURL.appendingPathComponent("predict"))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 120
        request.httpBody = try JSONEncoder().encode(body)
        let data = try await send(request)
        do {
            return try JSONDecoder().decode(PredictionResponse.self, from: data)
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
        case 503:
            throw MotivBotAPIError.modelLoading
        case 422:
            struct ErrorBody: Decodable { let detail: String }
            let detail = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.detail ?? ""
            throw MotivBotAPIError.invalidInput(detail)
        default:
            throw MotivBotAPIError.http(http.statusCode)
        }
    }
}
