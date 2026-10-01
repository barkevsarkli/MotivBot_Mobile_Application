import Foundation
import Testing
@testable import MotivBot

// MARK: - Fixtures

@MainActor
private enum Fixture {
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
            UserAnswer(sectionId: 1, questionId: 3, answerValue: .option(option(3, value: 3))),
            UserAnswer(sectionId: 1, questionId: 4, answerValue: .option(option(4, value: 3))),
            UserAnswer(sectionId: 1, questionId: 5, answerValue: .option(option(5, value: 0))),
            UserAnswer(sectionId: 1, questionId: 6, answerValue: .text("")),
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
        result.append(UserAnswer(sectionId: 6, questionId: 54, answerValue: .scale(4)))
        result.append(UserAnswer(sectionId: 6, questionId: 55, answerValue: .scale(6)))
        return result
    }

    static let responseJSON = """
    {
      "model_id": "amirbhat/motivbot-multilingual-XLMR-10062026v3",
      "inputs_used": {"Age": 25, "sex": 0.0, "PHQ-9": 10, "Rosenberg.self-esteem": 21},
      "dimensions": [
        {"key": "Anxiety_Fear_Worry", "level": "High", "probabilities": {"No": 0.05, "Low": 0.07, "High": 0.88}},
        {"key": "Depression_Hopelessness_Sadness", "level": "No", "probabilities": {"No": 0.84, "Low": 0.05, "High": 0.11}},
        {"key": "Self_Esteem_Issue", "level": "No", "probabilities": {"No": 0.94, "Low": 0.05, "High": 0.01}},
        {"key": "Positive_Affect", "level": "Low", "probabilities": {"No": 0.40, "Low": 0.45, "High": 0.15}},
        {"key": "Negative_Affect", "level": "High", "probabilities": {"No": 0.25, "Low": 0.36, "High": 0.39}}
      ],
      "elevated_risk": true
    }
    """

    static func response() throws -> PredictionResponse {
        try JSONDecoder().decode(PredictionResponse.self, from: Data(responseJSON.utf8))
    }
}

// MARK: - Request builder

@MainActor
struct PredictionRequestBuilderTests {

    @Test func mapsFullQuestionnaireToExactJSON() throws {
        let request = try PredictionRequestBuilder.build(
            emotionText: "  Herkes beni sinirlendiriyor, artık tahammülüm kalmadı \n",
            answers: Fixture.answers(),
            languageCode: "tr"
        )
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(request)) as! NSDictionary
        let expected: NSDictionary = [
            "text": "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı",
            "age": 25,
            "gender": "male",
            "phq9_items": [1, 2, 1, 0, 2, 1, 1, 2, 0],
            "rses_items": [3, 2, 1, 2, 0, 3, 2, 1, 1, 0],
            "language": "tr",
        ]
        #expect(json == expected)
    }

    @Test func excludesAttentionCheck() throws {
        let request = try PredictionRequestBuilder.build(
            emotionText: "text",
            answers: Fixture.answers(phq9: Array(repeating: 0, count: 9), attentionCheck: 3),
            languageCode: nil
        )
        #expect(request.phq9Items == Array(repeating: 0, count: 9))
    }

    @Test func sendsRawRosenbergValues() throws {
        // Reverse scoring happens on the server only.
        let raw = [3, 3, 3, 3, 3, 0, 0, 0, 0, 0]
        let request = try PredictionRequestBuilder.build(emotionText: "text", answers: Fixture.answers(rses: raw), languageCode: nil)
        #expect(request.rsesItems == raw)
    }

    @Test(arguments: [
        (1, PredictionRequest.Gender.male),
        (2, PredictionRequest.Gender.female),
        (3, PredictionRequest.Gender.unspecified),
    ])
    func mapsGender(optionValue: Int, expected: PredictionRequest.Gender) throws {
        let request = try PredictionRequestBuilder.build(
            emotionText: "text",
            answers: Fixture.answers(genderValue: optionValue),
            languageCode: nil
        )
        #expect(request.gender == expected)
    }

    @Test func rejectsMissingAnswers() {
        let answers = Fixture.answers().filter { $0.questionId != 30 }
        #expect(throws: PredictionRequestBuilder.BuildError.missingAnswer(questionId: 30)) {
            try PredictionRequestBuilder.build(emotionText: "text", answers: answers, languageCode: nil)
        }
    }

    @Test func rejectsEmptyText() {
        #expect(throws: PredictionRequestBuilder.BuildError.emptyText) {
            try PredictionRequestBuilder.build(emotionText: "  \n", answers: Fixture.answers(), languageCode: nil)
        }
    }

    @Test(arguments: [("25", 25), (" 42 ", 42), ("10", 10), ("100", 100), ("۲۵", 25), ("٣٠", 30)])
    func parsesValidAge(input: String, expected: Int) {
        #expect(PredictionRequestBuilder.parseAge(input) == expected)
    }

    @Test(arguments: ["", "abc", "9", "101", "2.5", "-20", "25a", "1000"])
    func rejectsInvalidAge(input: String) {
        #expect(PredictionRequestBuilder.parseAge(input) == nil)
    }
}

// MARK: - Response decoding

@MainActor
struct PredictionResponseTests {

    @Test func decodesSampleJSON() throws {
        let response = try Fixture.response()
        #expect(response.modelId == "amirbhat/motivbot-multilingual-XLMR-10062026v3")
        #expect(response.inputsUsed == .init(age: 25, sex: 0.0, phq9: 10, rosenberg: 21))
        #expect(response.dimensions.map(\.key) == WellBeingDimension.allCases.map(\.rawValue))
        #expect(response.dimensions[0].level == .high)
        #expect(response.dimensions[0].probabilities.high == 0.88)
        #expect(response.dimensions[3].level == .low)
        #expect(response.elevatedRisk)
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
private final class StubService: PredictionServicing {
    var healthStatuses: [HealthStatus] = [.ready]
    var result: Result<PredictionResponse, MotivBotAPIError>
    private(set) var sentRequests: [PredictionRequest] = []

    init(result: Result<PredictionResponse, MotivBotAPIError>) {
        self.result = result
    }

    func health() async throws -> HealthStatus {
        if case .failure(.serverUnreachable) = result { throw MotivBotAPIError.serverUnreachable }
        return healthStatuses.count > 1 ? healthStatuses.removeFirst() : healthStatuses[0]
    }

    func predict(_ request: PredictionRequest) async throws -> PredictionResponse {
        sentRequests.append(request)
        return try result.get()
    }
}

@MainActor
struct WellBeingViewModelPredictionTests {

    private func makeViewModel(_ service: StubService) -> WellBeingViewModel {
        let viewModel = WellBeingViewModel(service: service, healthPollInterval: .milliseconds(1), maxHealthPolls: 5)
        viewModel.emotionText = "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı"
        viewModel.answers = Fixture.answers()
        viewModel.languageCode = "fa"
        return viewModel
    }

    @Test func elevatedRiskReachesState() async throws {
        let service = StubService(result: .success(try Fixture.response()))
        service.healthStatuses = [.loading, .loading, .ready]
        let viewModel = makeViewModel(service)

        await viewModel.submitForAnalysis()

        #expect(viewModel.prediction?.elevatedRisk == true)
        #expect(viewModel.currentScreen == .result)
        #expect(viewModel.analysisPhase == .idle)
        #expect(service.sentRequests.first?.language == "fa")
        #expect(viewModel.generateCSV().contains("elevated_risk,true"))
        #expect(viewModel.generateCSV().contains("Anxiety_Fear_Worry,High"))
    }

    @Test func unreachableServerKeepsUserOnLoadingWithError() async {
        let viewModel = makeViewModel(StubService(result: .failure(.serverUnreachable)))
        viewModel.currentScreen = .loading

        await viewModel.submitForAnalysis()

        #expect(viewModel.analysisPhase == .failed(.serverUnreachable))
        #expect(viewModel.prediction == nil)
        #expect(viewModel.currentScreen == .loading)
    }

    @Test func modelThatNeverFinishesLoadingFailsWithModelLoading() async throws {
        let service = StubService(result: .success(try Fixture.response()))
        service.healthStatuses = [.loading]
        let viewModel = makeViewModel(service)

        await viewModel.submitForAnalysis()

        #expect(viewModel.analysisPhase == .failed(.modelLoading))
        #expect(service.sentRequests.isEmpty)
    }

    @Test func ageMustBeValidToProceed() {
        let viewModel = WellBeingViewModel(service: StubService(result: .failure(.decoding)))
        #expect(viewModel.currentQuestion?.id == 1)
        viewModel.freeTextAnswer = "abc"
        #expect(!viewModel.canProceedQuestion)
        viewModel.freeTextAnswer = "7"
        #expect(!viewModel.canProceedQuestion)
        viewModel.freeTextAnswer = "۲۵"
        #expect(viewModel.canProceedQuestion)
        viewModel.nextQuestion()
        guard case .text("25")? = viewModel.answers.first?.answerValue else {
            Issue.record("age should be stored as normalised digits")
            return
        }
    }

    @Test func treatmentQuestionIsOptional() {
        let viewModel = WellBeingViewModel(service: StubService(result: .failure(.decoding)))
        viewModel.currentQuestionIndex = 5
        #expect(viewModel.currentQuestion?.id == 6)
        viewModel.freeTextAnswer = ""
        #expect(viewModel.canProceedQuestion)
    }
}
