import Foundation

// MARK: - Saved data

/// An answer as stored on disk. Options are stored by id and looked up in `TestData` on load.
struct SavedAnswer: Codable, Equatable {
    enum Value: Codable, Equatable {
        case option(id: Int)
        case scale(Int)
        case text(String)
    }

    let sectionId: Int
    let questionId: Int
    let value: Value
}

/// An assessment in progress. Saved after every change and deleted once the results are shown.
struct SavedSession: Codable, Equatable {
    static let currentVersion = 1

    enum Stage: String, Codable {
        case emotionInput
        case questionnaire
        /// All questions were answered; the analysis had started.
        case analysis
    }

    var version = SavedSession.currentVersion
    var language: AppLanguage
    var emotionText: String
    var answers: [SavedAnswer]
    var stage: Stage
    var sectionIndex: Int
    var questionIndex: Int
    var startedAt: Date
}

/// The last result, kept until restart so it can still be exported after the app closes.
struct SavedResult: Codable, Equatable {
    static let currentVersion = 1

    var version = SavedResult.currentVersion
    var language: AppLanguage
    var completedAt: Date
    var prediction: PredictionResponse
    var elevatedRisk: Bool
}

extension UserAnswer {
    var saved: SavedAnswer {
        let value: SavedAnswer.Value
        switch answerValue {
        case .option(let option): value = .option(id: option.id)
        case .scale(let scale): value = .scale(scale)
        case .text(let text): value = .text(text)
        }
        return SavedAnswer(sectionId: sectionId, questionId: questionId, value: value)
    }

    /// Nil when the question or option no longer exists in `sections`.
    init?(_ saved: SavedAnswer, sections: [TestSection]) {
        guard let question = sections.first(where: { $0.id == saved.sectionId })?
            .questions.first(where: { $0.id == saved.questionId }) else { return nil }
        let value: AnswerValue
        switch saved.value {
        case .option(let id):
            guard let option = question.options?.first(where: { $0.id == id }) else { return nil }
            value = .option(option)
        case .scale(let scale):
            value = .scale(scale)
        case .text(let text):
            value = .text(text)
        }
        self.init(sectionId: saved.sectionId, questionId: saved.questionId, answerValue: value)
    }
}

// MARK: - Store

protocol SessionStoring {
    /// The saved session, or nil if there is none, it expired, or its version is unknown.
    func loadSession() -> SavedSession?
    func saveSession(_ session: SavedSession)
    func deleteSession()

    func loadResult() -> SavedResult?
    func saveResult(_ result: SavedResult)
    func deleteResult()
}

/// JSON files in Application Support, readable only while the device is unlocked and excluded from backup.
/// Unreadable, expired and unknown-version files are deleted instead of loaded.
final class FileSessionStore: SessionStoring {

    static let defaultMaxAge: TimeInterval = 7 * 24 * 60 * 60

    static var defaultDirectory: URL {
        URL.applicationSupportDirectory.appendingPathComponent("Assessment", isDirectory: true)
    }

    private let directory: URL
    /// Sessions started longer ago than this are discarded.
    private let maxAge: TimeInterval
    private let now: () -> Date

    init(directory: URL = FileSessionStore.defaultDirectory, maxAge: TimeInterval = FileSessionStore.defaultMaxAge, now: @escaping () -> Date = Date.init) {
        self.directory = directory
        self.maxAge = maxAge
        self.now = now
    }

    private var sessionURL: URL { directory.appendingPathComponent("session.json") }
    private var resultURL: URL { directory.appendingPathComponent("result.json") }

    func loadSession() -> SavedSession? {
        guard let session: SavedSession = load(sessionURL, version: SavedSession.currentVersion) else { return nil }
        guard now().timeIntervalSince(session.startedAt) <= maxAge else {
            deleteSession()
            return nil
        }
        return session
    }

    func saveSession(_ session: SavedSession) { save(session, to: sessionURL) }
    func deleteSession() { try? FileManager.default.removeItem(at: sessionURL) }

    func loadResult() -> SavedResult? { load(resultURL, version: SavedResult.currentVersion) }
    func saveResult(_ result: SavedResult) { save(result, to: resultURL) }
    func deleteResult() { try? FileManager.default.removeItem(at: resultURL) }

    private struct Header: Decodable { let version: Int }

    private func load<Value: Decodable>(_ url: URL, version: Int) -> Value? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        let decoder = JSONDecoder()
        guard let header = try? decoder.decode(Header.self, from: data), header.version == version,
              let value = try? decoder.decode(Value.self, from: data) else {
            try? FileManager.default.removeItem(at: url)
            return nil
        }
        return value
    }

    private func save<Value: Encodable>(_ value: Value, to url: URL) {
        do {
            try prepareDirectory()
            let data = try JSONEncoder().encode(value)
            try data.write(to: url, options: [.atomic, .completeFileProtection])
        } catch {
            // Saving is best effort; the contents are never logged.
        }
    }

    private func prepareDirectory() throws {
        var directory = self.directory
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try directory.setResourceValues(values)
    }
}
