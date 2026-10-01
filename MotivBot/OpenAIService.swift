import Foundation

class OpenAIService {
    
    private let apiKey = "" // redacted: key removed before first commit
    private let endpoint = "https://api.openai.com/v1/chat/completions"
    
    
    struct ChatRequest: Codable {
        let model: String
        let messages: [ChatMessage]
        let max_tokens: Int
        let temperature: Double
        var response_format: ResponseFormat? = nil
    }

    struct ResponseFormat: Codable {
        let type: String

        static let json = ResponseFormat(type: "json_object")
    }
    
    struct ChatMessage: Codable {
        let role: String
        let content: String
    }
    
    struct ChatResponse: Codable {
        let choices: [Choice]
    }
    
    struct Choice: Codable {
        let message: ResponseMessage
    }
    
    struct ResponseMessage: Codable {
        let content: String
    }
    
    // MARK: - API Call
    
    /// Sends the user's emotional text and questionnaire answers to OpenAI
    /// and returns a motivational quote/response.
    func sendToOpenAI(prompt: String) async throws -> String {
        guard let url = URL(string: endpoint) else {
            throw OpenAIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 60
        
        let systemMessage = ChatMessage(
            role: "system",
            content: """
            You are a compassionate and insightful psychological well-being assistant. \
            Based on the user's emotional expression and questionnaire responses, \
            provide a personalized, uplifting motivational quote followed by a brief \
            (2-3 sentence) supportive message. Be warm, empathetic, and encouraging. \
            Do not diagnose or prescribe — only offer emotional support and positive reinforcement.
            """
        )
        
        let userMessage = ChatMessage(
            role: "user",
            content: prompt
        )
        
        let chatRequest = ChatRequest(
            model: "gpt-4o-mini",
            messages: [systemMessage, userMessage],
            max_tokens: 1000,
            temperature: 0.8
        )
        
        let jsonData = try JSONEncoder().encode(chatRequest)
        request.httpBody = jsonData
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw OpenAIError.invalidResponse
        }
        
        guard httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw OpenAIError.apiError(statusCode: httpResponse.statusCode, message: errorBody)
        }
        
        let chatResponse = try JSONDecoder().decode(ChatResponse.self, from: data)
        
        guard let firstChoice = chatResponse.choices.first else {
            throw OpenAIError.noContent
        }
        
        return firstChoice.message.content.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: - Motivation Cards

    /// JSON payload returned by the model when asked for motivation cards.
    private struct CardsPayload: Decodable {
        struct Item: Decodable {
            let section_code: String?
            let section_title: String?
            let quote: String
            let author: String?
            let message: String
        }
        let cards: [Item]
    }

    /// Asks the model for three motivational cards, each anchored to a different
    /// questionnaire section, and returns them decoded.
    func fetchMotivationCards(prompt: String, languageName: String) async throws -> [MotivationCard] {
        guard let url = URL(string: endpoint) else {
            throw OpenAIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.addValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.addValue("application/json", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 60

        let systemMessage = ChatMessage(
            role: "system",
            content: """
            You are a compassionate and insightful psychological well-being assistant. \
            You will receive a user's emotional expression and their answers to a \
            multi-section well-being questionnaire.

            Produce EXACTLY three motivational cards as JSON. Each card MUST be anchored \
            to a DIFFERENT questionnaire section: pick the three sections whose answers \
            are most meaningful for this user, and never repeat a section. Ground each \
            card's message in that section's specific answers.

            Write every quote and message in \(languageName).

            Return ONLY this JSON object, with no markdown fences:
            {
              "cards": [
                {
                  "section_code": "single letter of the section, e.g. B",
                  "section_title": "the section title as given to you",
                  "quote": "one short motivational quote, 1-2 sentences",
                  "author": "quote author, or null if original",
                  "message": "2-3 supportive sentences tied to that section's answers"
                }
              ]
            }

            Be warm, empathetic and encouraging. Do not diagnose or prescribe — \
            offer only emotional support and positive reinforcement.
            """
        )

        let chatRequest = ChatRequest(
            model: "gpt-4o-mini",
            messages: [systemMessage, ChatMessage(role: "user", content: prompt)],
            max_tokens: 900,
            temperature: 0.8,
            response_format: .json
        )

        request.httpBody = try JSONEncoder().encode(chatRequest)

        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw OpenAIError.invalidResponse
        }

        guard httpResponse.statusCode == 200 else {
            let errorBody = String(data: data, encoding: .utf8) ?? "Unknown error"
            throw OpenAIError.apiError(statusCode: httpResponse.statusCode, message: errorBody)
        }

        let chatResponse = try JSONDecoder().decode(ChatResponse.self, from: data)

        guard let content = chatResponse.choices.first?.message.content else {
            throw OpenAIError.noContent
        }

        let cleaned = Self.stripCodeFences(content)

        guard let payload = try? JSONDecoder().decode(CardsPayload.self, from: Data(cleaned.utf8)),
              !payload.cards.isEmpty else {
            throw OpenAIError.malformedCards
        }

        return payload.cards.prefix(3).enumerated().map { index, item in
            MotivationCard(
                id: index + 1,
                sectionCode: item.section_code?.trimmingCharacters(in: .whitespaces) ?? "",
                sectionTitle: item.section_title?.trimmingCharacters(in: .whitespaces) ?? "",
                quote: item.quote.trimmingCharacters(in: .whitespacesAndNewlines),
                author: item.author?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty,
                message: item.message.trimmingCharacters(in: .whitespacesAndNewlines)
            )
        }
    }

    /// Removes ```json fences if the model wraps the payload despite the JSON mode.
    private static func stripCodeFences(_ text: String) -> String {
        var trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("```") else { return trimmed }
        trimmed = trimmed.replacingOccurrences(of: "```json", with: "")
        trimmed = trimmed.replacingOccurrences(of: "```", with: "")
        return trimmed.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}

// MARK: - Error Types

enum OpenAIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case apiError(statusCode: Int, message: String)
    case noContent
    case malformedCards
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid API URL."
        case .invalidResponse:
            return "Invalid response from server."
        case .apiError(let statusCode, let message):
            return "API Error (\(statusCode)): \(message)"
        case .noContent:
            return "No content in API response."
        case .malformedCards:
            return "Could not read the motivation cards from the API response."
        }
    }
}
