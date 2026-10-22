import Foundation
import SwiftUI
import Testing
@testable import MotivBot

// MARK: - Recorded fixtures

/// Event streams recorded from the Space (message texts shortened).
enum RecordedStream {
    static let complete = """
    event: complete
    data: ["Detected Language: Turkish\\n\\n Emotion Scores\\n- **Anxiety_Fear_Worry**: 0.887", {"emotions": {"Anxiety_Fear_Worry": 0.8871341347694397, "Depression_Hopelessness_Sadness": 0.758378267288208, "Self_Esteem_Issue": 0.3113669753074646, "Positive_Affect": 0.03797024488449097, "Negative_Affect": 0.9802320003509521}, "messages": [{"category": "Emotional Validation", "author": "Matt Haig", "score": 2.5, "text": "Bir g\\u00fcn k\\u00fc\\u00e7\\u00fck bir \\u015fey i\\u00e7in co\\u015fkulu g\\u00f6zya\\u015flar\\u0131 d\\u00f6keceksin."}, {"category": "Cognitive Reframing", "author": "Geert Verschaeve", "score": 2.646, "text": "Kayg\\u0131 sen de\\u011filsin."}, {"category": "Action-Oriented Encouragement", "author": "Elizabeth Wurtzel", "score": 2.646, "text": "Kelimelerin g\\u00fc\\u00e7l\\u00fc oldu\\u011funa inan\\u0131yorum."}]}]


    """

    static let heartbeatThenComplete = "event: heartbeat\ndata: null\n\nevent: generating\ndata: null\n\n" + complete

    static let quotaExceeded = """
    event: error
    data: {"error": "You have exceeded your ZeroGPU runs limit. Authenticate with a Hugging Face token for more quota - https://huggingface.co/settings/tokens", "duration": 10, "visible": true, "title": "ZeroGPU quota exceeded"}


    """

    static let invalidChoice = """
    event: error
    data: {"error": "Value: Non-binary is not in the list of choices: ['Erkek', 'Kad\\u0131n', 'Male', 'Female', '\\u0645\\u0631\\u062f', '\\u0632\\u0646']", "duration": 10, "visible": true, "title": "Error"}


    """

    static let belowMinimum = "event: error\ndata: {\"error\": \"Value 5 is less than minimum value 10.\", \"duration\": 10, \"visible\": true, \"title\": \"Error\"}\n\n"
    static let nullError = "event: error\ndata: {\"error\": null}\n\n"
    static let unknownEvent = "event: error\ndata: \"404: Not Found\"\n\n"
    static let endsWithoutComplete = "event: heartbeat\ndata: null\n\nevent: generating\ndata: null\n\n"
}

// MARK: - Fakes

nonisolated struct ArrayByteIterator: AsyncIteratorProtocol {
    var bytes: [UInt8]
    var index = 0
    /// Thrown once the bytes run out, to simulate a dropped connection.
    var failure: URLError.Code?

    mutating func next() async throws -> UInt8? {
        guard index < bytes.count else {
            if let failure { throw URLError(failure) }
            return nil
        }
        defer { index += 1 }
        return bytes[index]
    }
}

@MainActor
final class FakeTransport: HTTPTransport {
    enum SubmitReply {
        case status(Int, String)
        case failure(URLError.Code)
        /// Waits until cancelled.
        case hang

        static let accepted = SubmitReply.status(200, #"{"event_id":"abc123"}"#)
    }

    enum StreamReply {
        case events(String)
        case dropped(String, URLError.Code)
        case status(Int)
    }

    /// Replies used in order; the last one repeats.
    var submitReplies: [SubmitReply] = [.accepted]
    var streamReplies: [StreamReply] = [.events(RecordedStream.complete)]
    var infoDelay: Duration = .zero

    private(set) var submitCount = 0
    private(set) var streamCount = 0
    private(set) var infoCount = 0
    private(set) var lastSubmit: URLRequest?
    private(set) var lastStream: URLRequest?

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        if request.url!.path.hasSuffix("gradio_api/info") {
            infoCount += 1
            try await Task.sleep(for: infoDelay)
            return (Data("{}".utf8), response(200, request))
        }
        submitCount += 1
        lastSubmit = request
        switch next(&submitReplies) {
        case .status(let code, let body):
            return (Data(body.utf8), response(code, request))
        case .failure(let code):
            throw URLError(code)
        case .hang:
            try await Task.sleep(for: .seconds(60))
            throw URLError(.timedOut)
        }
    }

    func lines(for request: URLRequest) async throws -> (HTTPURLResponse, any LineStream) {
        streamCount += 1
        lastStream = request
        switch next(&streamReplies) {
        case .events(let text):
            return (response(200, request), ByteLineStream(bytes: ArrayByteIterator(bytes: Array(text.utf8))))
        case .dropped(let text, let code):
            return (response(200, request), ByteLineStream(bytes: ArrayByteIterator(bytes: Array(text.utf8), failure: code)))
        case .status(let code):
            return (response(code, request), ByteLineStream(bytes: ArrayByteIterator(bytes: [])))
        }
    }

    private func next<Reply>(_ replies: inout [Reply]) -> Reply {
        replies.count > 1 ? replies.removeFirst() : replies[0]
    }

    private func response(_ code: Int, _ request: URLRequest) -> HTTPURLResponse {
        HTTPURLResponse(url: request.url!, statusCode: code, httpVersion: nil, headerFields: nil)!
    }
}

/// Time that only moves when the service sleeps.
@MainActor
final class FakeTime {
    var instant = ContinuousClock.now
    private(set) var sleeps: [Duration] = []

    var timing: MotivBotAPIService.Timing {
        MotivBotAPIService.Timing(
            now: { self.instant },
            sleep: { duration in
                try Task.checkCancellation()
                self.sleeps.append(duration)
                self.instant = self.instant.advanced(by: duration)
            },
            random: { 0.5 }
        )
    }
}

@MainActor
final class InMemorySessionStore: SessionStoring {
    var session: SavedSession?
    var result: SavedResult?

    func loadSession() -> SavedSession? { session }
    func saveSession(_ session: SavedSession) { self.session = session }
    func deleteSession() { session = nil }
    func loadResult() -> SavedResult? { result }
    func saveResult(_ result: SavedResult) { self.result = result }
    func deleteResult() { result = nil }
}

@MainActor
private func validRequest(text: String = "Bugün çok yorgun ve endişeli hissediyorum.") -> PredictionRequest {
    PredictionRequest(text: text, age: 25, gender: .female, phq9Items: Fixture.phq9, rsesItems: Fixture.rses)
}

// MARK: - Server-sent events

@MainActor
struct ServerSentEventTests {

    @Test func joinsMultiLineDataAndSkipsComments() {
        var parser = ServerSentEventParser()
        let lines = [": keep-alive", "event:complete", "data: [1,", "data:  2]", ""]
        let events = lines.compactMap { parser.consume($0) }
        #expect(events == [ServerSentEvent(event: "complete", data: "[1,\n 2]")])
    }

    @Test func dispatchesFinalEventWithoutTrailingBlankLine() {
        var parser = ServerSentEventParser()
        #expect(parser.consume("event: complete") == nil)
        #expect(parser.consume("data: x") == nil)
        #expect(parser.finish() == ServerSentEvent(event: "complete", data: "x"))
        #expect(parser.finish() == nil)
    }

    @Test func splitsCRLFAndKeepsEmptyLines() {
        var splitter = LineSplitter()
        let lines = Array("event: a\r\ndata: b\r\n\r\ntail".utf8).compactMap { splitter.append($0) }
        #expect(lines == ["event: a", "data: b", ""])
        #expect(splitter.finish() == "tail")
    }

    @Test func byteStreamSurfacesDroppedConnection() async throws {
        var stream = ByteLineStream(bytes: ArrayByteIterator(bytes: Array("event: heartbeat\n".utf8), failure: .networkConnectionLost))
        #expect(try await stream.next() == "event: heartbeat")
        do {
            _ = try await stream.next()
            Issue.record("expected the dropped connection to throw")
        } catch {
            #expect(error is URLError)
        }
    }

    @Test(arguments: [RecordedStream.complete, RecordedStream.heartbeatThenComplete])
    func decodesRecordedResult(stream: String) throws {
        let response = try MotivBotAPIService.parseEventStream(stream)
        #expect(response.messages.count == 3)
        #expect(response.messages.map(\.author) == ["Matt Haig", "Geert Verschaeve", "Elizabeth Wurtzel"])
        #expect(response.level(for: .negativeAffect) == .high)
        #expect(response.messages[1].text == "Kaygı sen değilsin.")
    }

    @Test func completeWithoutTrailingBlankLine() throws {
        let stream = RecordedStream.complete.trimmingCharacters(in: .newlines)
        #expect(try MotivBotAPIService.parseEventStream(stream).messages.count == 3)
    }

    @Test(arguments: [
        (RecordedStream.quotaExceeded, MotivBotAPIError.quotaExceeded),
        (RecordedStream.belowMinimum, .invalidInput("Value 5 is less than minimum value 10.")),
        (RecordedStream.nullError, .invalidInput("")),
        (RecordedStream.unknownEvent, .http(404)),
        (RecordedStream.endsWithoutComplete, .streamEnded),
    ])
    func classifiesRecordedFailures(stream: String, expected: MotivBotAPIError) {
        #expect(throws: expected) { try MotivBotAPIService.parseEventStream(stream) }
    }

    @Test func invalidChoiceIsInvalidInput() {
        #expect {
            try MotivBotAPIService.parseEventStream(RecordedStream.invalidChoice)
        } throws: { error in
            guard case .invalidInput(let message)? = error as? MotivBotAPIError else { return false }
            return message.contains("Non-binary is not in the list of choices")
        }
    }
}

// MARK: - Service

@MainActor
struct MotivBotAPIServiceTests {

    private func makeService(_ transport: FakeTransport, time: FakeTime? = nil) -> MotivBotAPIService {
        MotivBotAPIService(baseURL: URL(string: "https://example.test")!, transport: transport, timing: (time ?? FakeTime()).timing)
    }

    @Test func submitsNamedParametersThenStreamsResult() async throws {
        let transport = FakeTransport()
        var progress: [PredictionProgress] = []

        let response = try await makeService(transport).predict(validRequest()) { progress.append($0) }

        #expect(response.messages.count == 3)
        #expect(progress == [.connecting, .analysing])
        #expect(transport.lastSubmit?.url?.absoluteString == "https://example.test/gradio_api/call/v2/run_pipeline")
        #expect(transport.lastSubmit?.httpMethod == "POST")
        #expect(transport.lastStream?.url?.absoluteString == "https://example.test/gradio_api/call/run_pipeline/abc123")
        let body = try JSONSerialization.jsonObject(with: transport.lastSubmit!.httpBody!) as! [String: Any]
        #expect(Set(body.keys) == ["text", "age", "gender", "phq9", "rses"])
    }

    @Test func retriesWhileSpaceStartsWithExponentialBackoff() async throws {
        let transport = FakeTransport()
        transport.submitReplies = [.status(503, ""), .status(503, ""), .status(429, ""), .accepted]
        let time = FakeTime()
        var progress: [PredictionProgress] = []

        _ = try await makeService(transport, time: time).predict(validRequest()) { progress.append($0) }

        #expect(transport.submitCount == 4)
        #expect(time.sleeps == [.seconds(1), .seconds(2), .seconds(4)])
        #expect(progress.contains(.wakingUp))
    }

    @Test func givesUpWhenRetryBudgetIsSpent() async {
        let transport = FakeTransport()
        transport.submitReplies = [.status(503, "")]
        let time = FakeTime()

        await #expect(throws: MotivBotAPIError.spaceStarting) {
            try await makeService(transport, time: time).predict(validRequest()) { _ in }
        }
        // 1 + 2 + 4 + 8 + 16 + 16 + 16 + 16 = 79 s; one more 16 s wait would pass the 90 s budget.
        #expect(time.sleeps == [1, 2, 4, 8, 16, 16, 16, 16].map { .seconds($0) })
        #expect(transport.submitCount == 9)
    }

    @Test func jitterStaysWithinAQuarterOfTheBase() {
        let policy = MotivBotAPIService.RetryPolicy()
        #expect(policy.delay(afterAttempt: 3, random: 0) == .seconds(3))
        #expect(policy.delay(afterAttempt: 3, random: 0.5) == .seconds(4))
        #expect(policy.delay(afterAttempt: 3, random: 1) == .seconds(5))
        #expect(policy.delay(afterAttempt: 20, random: 0.5) == .seconds(16))
    }

    @Test func retriesTransientNetworkErrorsAndDroppedStreams() async throws {
        let transport = FakeTransport()
        transport.submitReplies = [.failure(.timedOut), .accepted]
        transport.streamReplies = [
            .dropped("event: heartbeat\ndata: null\n\n", .networkConnectionLost),
            .events(RecordedStream.endsWithoutComplete),
            .events(RecordedStream.complete),
        ]
        let time = FakeTime()

        _ = try await makeService(transport, time: time).predict(validRequest()) { _ in }

        #expect(transport.submitCount == 4)
        #expect(transport.streamCount == 3)
        #expect(time.sleeps.count == 3)
    }

    @Test(arguments: [
        (FakeTransport.SubmitReply.status(422, "bad"), MotivBotAPIError.invalidInput("bad")),
        (.status(500, ""), .http(500)),
        (.failure(.notConnectedToInternet), .offline),
        (.failure(.secureConnectionFailed), .network(.secureConnectionFailed)),
    ])
    func doesNotRetryPermanentFailures(reply: FakeTransport.SubmitReply, expected: MotivBotAPIError) async {
        let transport = FakeTransport()
        transport.submitReplies = [reply]
        await #expect(throws: expected) {
            try await makeService(transport).predict(validRequest()) { _ in }
        }
        #expect(transport.submitCount == 1)
    }

    @Test func doesNotRetryExhaustedQuota() async {
        let transport = FakeTransport()
        transport.streamReplies = [.events(RecordedStream.quotaExceeded)]
        await #expect(throws: MotivBotAPIError.quotaExceeded) {
            try await makeService(transport).predict(validRequest()) { _ in }
        }
        #expect(transport.submitCount == 1)
        #expect(MotivBotAPIError.quotaExceeded.analysisError == .serviceBusy)
    }

    @Test func validatesBeforeSending() async {
        let transport = FakeTransport()
        await #expect(throws: MotivBotAPIError.self) {
            try await makeService(transport).predict(validRequest(text: "  kısa  ")) { _ in }
        }
        #expect(transport.submitCount == 0)
    }

    @Test func stopsImmediatelyWhenCancelled() async {
        let transport = FakeTransport()
        transport.submitReplies = [.hang]
        let service = makeService(transport)
        let task = Task { try await service.predict(validRequest()) { _ in } }

        try? await Task.sleep(for: .milliseconds(50))
        task.cancel()
        let started = ContinuousClock.now
        let result = await task.result

        #expect(ContinuousClock.now - started < .seconds(5))
        #expect(throws: CancellationError.self) { try result.get() }
        #expect(transport.submitCount == 1)
    }

    @Test func cancelledURLRequestBecomesCancellationError() {
        #expect(MotivBotAPIService.mapTransportError(URLError(.cancelled)) is CancellationError)
    }

    @Test func prewarmRunsOnePingAtATime() async {
        let transport = FakeTransport()
        transport.infoDelay = .milliseconds(100)
        let service = makeService(transport)

        async let first: Void = service.prewarm()
        async let second: Void = service.prewarm()
        _ = await (first, second)
        #expect(transport.infoCount == 1)

        await service.prewarm()
        #expect(transport.infoCount == 2)
    }
}

// MARK: - Validation

@MainActor
struct ValidationTests {

    @Test func acceptsValidRequest() throws {
        try validRequest().validate()
    }

    /// (text, age, PHQ-9 items, RSES items)
    @Test(arguments: [
        ("123456789", 25, Fixture.phq9, Fixture.rses),
        (String(repeating: "a", count: 2001), 25, Fixture.phq9, Fixture.rses),
        ("yeterince uzun bir metin", 17, Fixture.phq9, Fixture.rses),
        ("yeterince uzun bir metin", 101, Fixture.phq9, Fixture.rses),
        ("yeterince uzun bir metin", 25, Array(Fixture.phq9.prefix(8)), Fixture.rses),
        ("yeterince uzun bir metin", 25, Fixture.phq9, Fixture.rses.map { $0 + 1 }),
    ])
    func rejectsInvalidRequest(text: String, age: Int, phq9: [Int], rses: [Int]) {
        let request = PredictionRequest(text: text, age: age, gender: .male, phq9Items: phq9, rsesItems: rses)
        #expect(throws: MotivBotAPIError.self) { try request.validate() }
    }

    @Test(arguments: [
        ("Bugün kendimi çok yorgun hissediyorum.", PipelineLanguage.turkish),
        ("ben bugun cok yorgunum ve mutsuzum", .turkish),
        ("Bugun kendimi kotu hissediyorum", .english),
        ("I feel very tired and anxious today.", .english),
        ("امروز خیلی خسته و نگران هستم", .persian),
        ("I feel سلام today and every day", .english),
    ])
    func detectsLanguageLikeThePipeline(text: String, expected: PipelineLanguage) {
        #expect(PipelineLanguage.detect(text) == expected)
    }
}

// MARK: - Language lock

@MainActor
struct LanguageLockTests {

    private func makeViewModel() -> WellBeingViewModel {
        WellBeingViewModel(service: StubService(.failure(.decoding)), store: InMemorySessionStore())
    }

    @Test func typingLocksLanguageAndClearingUnlocksIt() {
        let viewModel = makeViewModel()
        #expect(viewModel.canChangeLanguage)

        viewModel.emotionText = "B"
        #expect(!viewModel.canChangeLanguage)
        viewModel.localization.setLanguage(.persian)
        viewModel.localization.toggleLanguage()
        #expect(viewModel.localization.currentLanguage == .turkish)

        viewModel.emotionText = "  "
        #expect(viewModel.canChangeLanguage)
    }

    @Test func languageStaysLockedThroughTheQuestionnaireUntilRestart() {
        let viewModel = makeViewModel()
        viewModel.emotionText = "Bugün kendimi çok yorgun hissediyorum."
        viewModel.submitEmotionAndProceed()
        #expect(viewModel.currentScreen == .questionnaire)
        #expect(viewModel.localization.isLocked)
        #expect(!viewModel.canChangeLanguage)

        viewModel.restartAssessment()
        #expect(!viewModel.localization.isLocked)
        #expect(viewModel.canChangeLanguage)
    }

    @Test func refusesTextInAnotherLanguage() {
        let viewModel = makeViewModel()
        viewModel.emotionText = "امروز خیلی خسته و نگران هستم"
        #expect(viewModel.emotionTextStatus == .wrongLanguage)
        #expect(viewModel.emotionTextWarningKey == "emotion_wrong_language")
        #expect(!viewModel.canProceedEmotion)
        viewModel.submitEmotionAndProceed()
        #expect(viewModel.currentScreen == .emotionInput)

        viewModel.emotionText = ""
        viewModel.localization.setLanguage(.persian)
        viewModel.emotionText = "امروز خیلی خسته و نگران هستم"
        #expect(viewModel.canProceedEmotion)
        #expect(viewModel.localization.layoutDirection == .rightToLeft)
    }

    @Test func flagsTooLongText() {
        let viewModel = makeViewModel()
        viewModel.emotionText = String(repeating: "çok ", count: 600)
        #expect(viewModel.emotionTextStatus == .tooLong)
        #expect(viewModel.emotionTextWarningKey == "emotion_too_long")
    }
}

// MARK: - Persistence

@MainActor
struct PersistenceTests {

    private let persianText = "امروز خیلی خسته و نگران هستم"

    private func temporaryDirectory() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    }

    private func session(startedAt: Date = Date()) -> SavedSession {
        SavedSession(language: .persian, emotionText: persianText, answers: [SavedAnswer(sectionId: 1, questionId: 1, value: .text("30"))],
                     stage: .questionnaire, sectionIndex: 0, questionIndex: 1, startedAt: startedAt)
    }

    @Test func fileStoreRoundTripsAndExcludesFromBackup() throws {
        let directory = temporaryDirectory()
        let store = FileSessionStore(directory: directory)
        let saved = session()
        store.saveSession(saved)

        #expect(store.loadSession() == saved)
        let values = try directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(values.isExcludedFromBackup == true)

        store.deleteSession()
        #expect(store.loadSession() == nil)
    }

    @Test func expiredSessionIsDiscarded() {
        let directory = temporaryDirectory()
        let started = Date()
        FileSessionStore(directory: directory).saveSession(session(startedAt: started))

        let sixDaysLater = FileSessionStore(directory: directory, now: { started.addingTimeInterval(6 * 86_400) })
        #expect(sixDaysLater.loadSession() != nil)
        let eightDaysLater = FileSessionStore(directory: directory, now: { started.addingTimeInterval(8 * 86_400) })
        #expect(eightDaysLater.loadSession() == nil)
        #expect(FileSessionStore(directory: directory, now: { started }).loadSession() == nil)
    }

    @Test(arguments: [#"{"version": 99, "language": "tr"}"#, "not json"])
    func unknownOrCorruptFileIsDiscarded(contents: String) throws {
        let directory = temporaryDirectory()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("session.json")
        try Data(contents.utf8).write(to: file)

        #expect(FileSessionStore(directory: directory).loadSession() == nil)
        #expect(!FileManager.default.fileExists(atPath: file.path))
    }

    @Test func resumeRestoresLanguageAnswersAndPosition() {
        let store = FileSessionStore(directory: temporaryDirectory())
        let first = WellBeingViewModel(service: StubService(.failure(.decoding)), store: store)
        first.localization.setLanguage(.persian)
        first.emotionText = persianText
        first.submitEmotionAndProceed()
        first.freeTextAnswer = "30"
        first.nextQuestion()
        first.selectOption(2)
        first.nextQuestion()
        first.flushPendingSave()

        // Relaunch with a fresh, Turkish localization.
        let relaunched = WellBeingViewModel(service: StubService(.failure(.decoding)), store: store)
        #expect(relaunched.resumableSession?.language == .persian)
        #expect(relaunched.resumableSession?.answeredQuestions == 2)
        #expect(!relaunched.canChangeLanguage)

        relaunched.continueSession()
        #expect(relaunched.localization.currentLanguage == .persian)
        #expect(relaunched.localization.isLocked)
        #expect(relaunched.currentScreen == .questionnaire)
        #expect(relaunched.emotionText == persianText)
        #expect(relaunched.answers.count == 2)
        #expect(relaunched.currentQuestion?.id == 5)
        #expect(relaunched.resumableSession == nil)
    }

    @Test func startOverDiscardsSavedSession() {
        let store = InMemorySessionStore()
        store.session = session()
        let viewModel = WellBeingViewModel(service: StubService(.failure(.decoding)), store: store)
        #expect(viewModel.resumableSession != nil)

        viewModel.startOver()
        #expect(store.session == nil)
        #expect(viewModel.resumableSession == nil)
        #expect(viewModel.canChangeLanguage)
    }

    @Test func sessionReferringToUnknownOptionIsDiscarded() {
        let store = InMemorySessionStore()
        var broken = session()
        broken.answers = [SavedAnswer(sectionId: 1, questionId: 2, value: .option(id: 99))]
        store.session = broken

        let viewModel = WellBeingViewModel(service: StubService(.failure(.decoding)), store: store)
        #expect(viewModel.resumableSession == nil)
        #expect(store.session == nil)
    }

    @Test func resultReplacesSessionAndSurvivesRelaunchUntilRestart() async throws {
        let store = InMemorySessionStore()
        let viewModel = WellBeingViewModel(service: StubService(.success(try Fixture.response())), store: store)
        viewModel.emotionText = "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı"
        viewModel.answers = Fixture.answers()
        viewModel.flushPendingSave()
        #expect(store.session != nil)

        await viewModel.submitForAnalysis()
        #expect(store.session == nil)
        #expect(store.result?.language == .turkish)
        #expect(store.result?.elevatedRisk == true)

        let relaunched = WellBeingViewModel(service: StubService(.failure(.decoding)), store: store)
        #expect(relaunched.currentScreen == .result)
        #expect(relaunched.localization.isLocked)
        let summary = try #require(relaunched.resultSummary)
        #expect(summary.dimensions.count == 5)
        #expect(summary.messages.count == 3)
        #expect(summary.elevatedRisk)
        #expect(summary.language == .turkish)

        relaunched.restartAssessment()
        #expect(store.result == nil)
        #expect(relaunched.currentScreen == .emotionInput)
        #expect(!relaunched.localization.isLocked)
    }
}
