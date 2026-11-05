import Foundation
import Testing
@testable import MotivBot

// MARK: - Fixtures

@MainActor
enum Fixture {
    nonisolated static let phq9 = [1, 2, 1, 0, 2, 1, 1, 2, 0]
    nonisolated static let rses = [3, 2, 1, 2, 0, 3, 2, 1, 1, 0]

    static func option(_ questionId: Int, value: Int) -> AnswerOption {
        let question = TestData.sections.flatMap(\.questions).first { $0.id == questionId }!
        return question.options!.first { $0.value == value }!
    }

    /// A fully answered questionnaire, in the order the app records it.
    static func answers(
        age: String = "25",
        genderValue: Int = 1,
        phq9: [Int] = phq9,
        attentionCheck: Int = 0,
        rses: [Int] = rses
    ) -> [UserAnswer] {
        var result: [UserAnswer] = [
            UserAnswer(sectionId: 1, questionId: 1, answerValue: .text(age)),
            UserAnswer(sectionId: 1, questionId: 2, answerValue: .option(option(2, value: genderValue))),
            // "No" to the diagnosis question, so the treatment question is skipped.
            UserAnswer(sectionId: 1, questionId: 5, answerValue: .option(option(5, value: 0))),
        ]
        for (index, value) in phq9.enumerated() {
            result.append(UserAnswer(sectionId: 2, questionId: 7 + index, answerValue: .option(option(7 + index, value: value))))
        }
        result.append(UserAnswer(sectionId: 2, questionId: 16, answerValue: .option(option(16, value: attentionCheck))))
        for id in 17...23 {
            result.append(UserAnswer(sectionId: 3, questionId: id, answerValue: .option(option(id, value: 2))))
        }
        for (index, value) in rses.enumerated() {
            result.append(UserAnswer(sectionId: 4, questionId: 24 + index, answerValue: .option(option(24 + index, value: value))))
        }
        return result
    }

    /// The structured JSON of `data[1]`, as returned by the pipeline.
    static let structuredJSON = """
    {"emotions": {"Anxiety_Fear_Worry": 0.88, "Depression_Hopelessness_Sadness": 0.62, "Self_Esteem_Issue": 0.19, "Positive_Affect": 0.03, "Negative_Affect": 0.97}, "messages": [{"category": "Emotional Validation", "author": "Matt Haig", "score": 2.381, "text": "Hi\\u00e7bir \\u015fey sonsuza kadar s\\u00fcrmez."}, {"category": "Cognitive Reframing", "score": 2.501, "text": "Kayg\\u0131 sen de\\u011filsin."}, {"category": "Action-Oriented Encouragement", "score": 2.501, "text": "Korkunun \\u00fcstesinden gelebilirsin."}]}
    """

    static func response() throws -> PredictionResponse {
        try JSONDecoder().decode(PredictionResponse.self, from: Data(structuredJSON.utf8))
    }

    static func response(depression: Double) -> PredictionResponse {
        PredictionResponse(
            emotions: ["Anxiety_Fear_Worry": 0.1, "Depression_Hopelessness_Sadness": depression,
                       "Self_Esteem_Issue": 0.1, "Positive_Affect": 0.6, "Negative_Affect": 0.2],
            messages: []
        )
    }
}

// MARK: - Request builder

@MainActor
struct PredictionRequestBuilderTests {

    @Test func encodesNamedPipelineParameters() throws {
        let request = try PredictionRequestBuilder.build(
            emotionText: "  Herkes beni sinirlendiriyor, artık tahammülüm kalmadı \n",
            answers: Fixture.answers()
        )
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(request)) as! NSDictionary
        // PHQ-9 = 10; RSES = 24 on the 0-30 scale, sent as 34 on the pipeline's 10-40 scale.
        let expected: NSDictionary = [
            "text": "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı",
            "age": 25, "gender": "Erkek", "phq9": 10, "rses": 34,
        ]
        #expect(json == expected)
    }

    @Test func excludesAttentionCheck() throws {
        let request = try PredictionRequestBuilder.build(
            emotionText: "text",
            answers: Fixture.answers(phq9: Array(repeating: 0, count: 9), attentionCheck: 3)
        )
        #expect(request.phq9Items == Array(repeating: 0, count: 9))
        #expect(request.phq9Score == 0)
    }

    @Test func phq9ScoreIsSumOfItems() throws {
        let request = try PredictionRequestBuilder.build(emotionText: "text", answers: Fixture.answers(phq9: Array(repeating: 3, count: 9)))
        #expect(request.phq9Score == 27)
    }

    @Test(arguments: [
        // Agreeing with every positive statement and disagreeing with every negative one is the maximum.
        ([3, 3, 0, 3, 0, 3, 3, 0, 0, 0], 30),
        ([0, 0, 3, 0, 3, 0, 0, 3, 3, 3], 0),
        ([3, 3, 3, 3, 3, 3, 3, 3, 3, 3], 15),
        ([3, 2, 1, 2, 0, 3, 2, 1, 1, 0], 24),
    ])
    func rsesScoreAppliesReverseItems(items: [Int], expected: Int) throws {
        let request = try PredictionRequestBuilder.build(emotionText: "text", answers: Fixture.answers(rses: items))
        #expect(request.rsesItems == items)
        #expect(request.rsesScore == expected)
        #expect(request.pipelineRSESScore == expected + 10)
    }

    @Test(arguments: [
        (1, PredictionRequest.Gender.male, "Erkek", 0.0),
        (2, PredictionRequest.Gender.female, "Kadın", 1.0),
        (3, PredictionRequest.Gender.unspecified, "Non-binary", 0.5),
    ])
    func mapsGender(optionValue: Int, expected: PredictionRequest.Gender, wire: String, numeric: Double) throws {
        let request = try PredictionRequestBuilder.build(emotionText: "text", answers: Fixture.answers(genderValue: optionValue))
        #expect(request.gender == expected)
        #expect(request.gender.rawValue == wire)
        #expect(request.gender.numericValue == numeric)
    }

    @Test func rejectsMissingAnswers() {
        let answers = Fixture.answers().filter { $0.questionId != 30 }
        #expect(throws: PredictionRequestBuilder.BuildError.missingAnswer(questionId: 30)) {
            try PredictionRequestBuilder.build(emotionText: "text", answers: answers)
        }
    }

    @Test func rejectsEmptyText() {
        #expect(throws: PredictionRequestBuilder.BuildError.emptyText) {
            try PredictionRequestBuilder.build(emotionText: "  \n", answers: Fixture.answers())
        }
    }

    @Test(arguments: [("25", 25), (" 42 ", 42), ("18", 18), ("100", 100), ("۲۵", 25), ("٣٠", 30)])
    func parsesValidAge(input: String, expected: Int) {
        #expect(PredictionRequestBuilder.parseAge(input) == expected)
    }

    @Test(arguments: ["", "abc", "9", "17", "101", "2.5", "-20", "25a", "1000"])
    func rejectsInvalidAge(input: String) {
        #expect(PredictionRequestBuilder.parseAge(input) == nil)
    }
}

// MARK: - Response decoding

@MainActor
struct PredictionResponseTests {

    @Test func decodesStructuredJSON() throws {
        let response = try Fixture.response()
        #expect(response.probability(for: .anxiety) == 0.88)
        #expect(response.level(for: .anxiety) == .high)
        #expect(response.level(for: .selfEsteem) == .no)
        #expect(response.messages.count == 3)
        #expect(response.messages[0].category == "Emotional Validation")
        #expect(response.messages[0].text == "Hiçbir şey sonsuza kadar sürmez.")
        #expect(response.messages[0].author == "Matt Haig")
        #expect(response.messages[1].author == nil)
    }

    @Test(arguments: [(0.0, PredictionResponse.Level.no), (0.24, .no), (0.25, .low), (0.49, .low), (0.5, .high), (1.0, .high)])
    func levelThresholds(probability: Double, expected: PredictionResponse.Level) {
        let response = PredictionResponse(emotions: ["Anxiety_Fear_Worry": probability], messages: [])
        #expect(response.level(for: .anxiety) == expected)
        #expect(response.level(for: .depression) == nil)
    }

    @Test func parsesCompleteEventWithObjectPayload() throws {
        let stream = "event: complete\ndata: [\"### Summary\", \(Fixture.structuredJSON.trimmingCharacters(in: .whitespacesAndNewlines))]\n\n"
        let response = try MotivBotAPIService.parseEventStream(stream)
        #expect(response == (try Fixture.response()))
    }

    @Test func parsesCompleteEventWithEscapedStringPayload() throws {
        let inner = Fixture.structuredJSON.trimmingCharacters(in: .whitespacesAndNewlines)
        let escaped = String(data: try JSONSerialization.data(withJSONObject: [inner], options: .fragmentsAllowed), encoding: .utf8)!
            .dropFirst().dropLast()
        let stream = "event: generating\ndata: null\n\nevent: complete\ndata: [\"### Summary\", \(escaped)]\n\n"
        let response = try MotivBotAPIService.parseEventStream(stream)
        #expect(response == (try Fixture.response()))
    }

    @Test func errorEventBecomesInvalidInput() {
        let stream = "event: error\ndata: {\"error\": \"Value 15 is less than minimum value 18.\"}\n\n"
        #expect(throws: MotivBotAPIError.invalidInput("Value 15 is less than minimum value 18.")) {
            try MotivBotAPIService.parseEventStream(stream)
        }
    }

    @Test func positiveAffectToneIsInverted() {
        #expect(WellBeingDimension.positiveAffect.tone(for: .high) == .steady)
        #expect(WellBeingDimension.positiveAffect.tone(for: .no) == .care)
        #expect(WellBeingDimension.anxiety.tone(for: .high) == .care)
        #expect(WellBeingDimension.anxiety.tone(for: .no) == .steady)
    }
}

// MARK: - View model

@MainActor
final class StubService: PredictionServicing {
    /// Results returned in order; the last one repeats.
    var results: [Result<PredictionResponse, MotivBotAPIError>]
    private(set) var sentRequests: [PredictionRequest] = []

    init(_ results: Result<PredictionResponse, MotivBotAPIError>...) {
        self.results = results
    }

    private(set) var prewarmCount = 0

    func predict(_ request: PredictionRequest, progress: @escaping (PredictionProgress) -> Void) async throws -> PredictionResponse {
        sentRequests.append(request)
        progress(.analysing)
        let result = results.count > 1 ? results.removeFirst() : results[0]
        return try result.get()
    }

    func prewarm() async {
        prewarmCount += 1
    }
}

@MainActor
struct WellBeingViewModelPredictionTests {

    private func makeViewModel(_ service: StubService, phq9: [Int] = Fixture.phq9) -> WellBeingViewModel {
        let viewModel = WellBeingViewModel(service: service, store: InMemorySessionStore())
        viewModel.emotionText = "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı"
        viewModel.answers = Fixture.answers(phq9: phq9)
        return viewModel
    }

    @Test func showsResult() async throws {
        let service = StubService(.success(try Fixture.response()))
        let viewModel = makeViewModel(service)

        await viewModel.submitForAnalysis()

        #expect(service.sentRequests.count == 1)
        #expect(viewModel.currentScreen == .result)
        #expect(viewModel.analysisPhase == .idle)
        #expect(viewModel.prediction?.messages.count == 3)
        // Depression probability 0.62 is above the presence threshold.
        #expect(viewModel.elevatedRisk)
        let csv = viewModel.generateCSV()
        #expect(csv.contains("PHQ-9 (0-27),10"))
        #expect(csv.contains("RSES (0-30),24"))
        #expect(csv.contains("RSES sent to model (10-40),34"))
        #expect(csv.contains("(0 = male; 0.5 = not specified; 1 = female),0.0"))
        #expect(csv.contains("Anxiety_Fear_Worry,0.880,High"))
        #expect(csv.contains("elevated_risk,true"))
    }

    @Test func selfHarmItemRaisesRiskEvenWithLowDepression() async {
        var phq9 = Array(repeating: 0, count: 9)
        phq9[8] = 1
        let viewModel = makeViewModel(StubService(.success(Fixture.response(depression: 0.1))), phq9: phq9)
        await viewModel.submitForAnalysis()
        #expect(viewModel.elevatedRisk)
    }

    @Test func noRiskWithoutSignals() async {
        let viewModel = makeViewModel(StubService(.success(Fixture.response(depression: 0.1))), phq9: Array(repeating: 0, count: 9))
        await viewModel.submitForAnalysis()
        #expect(!viewModel.elevatedRisk)
    }

    @Test func unreachableServerKeepsUserOnLoadingWithError() async {
        let viewModel = makeViewModel(StubService(.failure(.offline)))
        viewModel.currentScreen = .loading

        await viewModel.submitForAnalysis()

        #expect(viewModel.analysisPhase == .failed(.offline))
        #expect(viewModel.prediction == nil)
        #expect(viewModel.currentScreen == .loading)
    }

    @Test(arguments: [
        (MotivBotAPIError.spaceStarting, AnalysisError.serviceWakingUp),
        (.quotaExceeded, .serviceBusy),
        (.rateLimited, .serviceBusy),
        (.network(.timedOut), .offline),
        (.decoding, .unexpected),
        (.invalidInput("Value 5 is less than minimum value 10."), .unexpected),
    ])
    func mapsServiceErrorsToUserFacingErrors(error: MotivBotAPIError, expected: AnalysisError) async {
        let viewModel = makeViewModel(StubService(.failure(error)))
        await viewModel.submitForAnalysis()
        #expect(viewModel.analysisPhase == .failed(expected))
    }

    @Test func ageMustBeValidToProceed() {
        let viewModel = WellBeingViewModel(service: StubService(.failure(.decoding)), store: InMemorySessionStore())
        #expect(viewModel.currentQuestion?.id == 1)
        viewModel.freeTextAnswer = "abc"
        #expect(!viewModel.canProceedQuestion)
        viewModel.freeTextAnswer = "17"
        #expect(!viewModel.canProceedQuestion)
        viewModel.freeTextAnswer = "۲۵"
        #expect(viewModel.canProceedQuestion)
        viewModel.nextQuestion()
        guard case .text("25")? = viewModel.answers.first?.answerValue else {
            Issue.record("age should be stored as normalised digits")
            return
        }
    }

    /// Answers age and gender, then the diagnosis question with `diagnosisOptionId` (1 = yes, 2 = no).
    private func answerDemographics(diagnosisOptionId: Int) -> WellBeingViewModel {
        let viewModel = WellBeingViewModel(service: StubService(.failure(.decoding)), store: InMemorySessionStore())
        viewModel.freeTextAnswer = "30"
        viewModel.nextQuestion()
        viewModel.selectOption(2)
        viewModel.nextQuestion()
        #expect(viewModel.currentQuestion?.id == 5)
        viewModel.selectOption(diagnosisOptionId)
        viewModel.nextQuestion()
        return viewModel
    }

    @Test func demographicsAskNoIncomeOrEducation() {
        #expect(TestData.sections[0].questions.map(\.id) == [1, 2, 5, 6])
    }

    @Test func noDiagnosisSkipsTreatmentQuestion() {
        let viewModel = answerDemographics(diagnosisOptionId: 2)
        #expect(viewModel.currentSectionIndex == 1)
        #expect(viewModel.currentQuestion?.id == 7)
        #expect(!viewModel.answers.contains { $0.questionId == 6 })
        #expect(viewModel.shownQuestionCount(in: viewModel.sections[0]) == 3)
    }

    @Test func diagnosisMakesTreatmentQuestionRequired() {
        let viewModel = answerDemographics(diagnosisOptionId: 1)
        #expect(viewModel.currentQuestion?.id == 6)
        viewModel.freeTextAnswer = "  "
        #expect(!viewModel.canProceedQuestion)
        viewModel.freeTextAnswer = "Bireysel psikoterapi"
        #expect(viewModel.canProceedQuestion)
        viewModel.nextQuestion()
        #expect(viewModel.currentQuestion?.id == 7)
    }

    @Test func goingBackPastSkippedQuestionReturnsToDiagnosis() {
        let viewModel = answerDemographics(diagnosisOptionId: 2)
        viewModel.previousQuestion()
        #expect(viewModel.currentQuestion?.id == 5)
        #expect(viewModel.answers.map(\.questionId) == [1, 2])

        viewModel.selectOption(1)
        viewModel.nextQuestion()
        #expect(viewModel.currentQuestion?.id == 6)
    }
}
