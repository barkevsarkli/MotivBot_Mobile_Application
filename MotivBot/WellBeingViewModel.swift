import Foundation
import SwiftUI
import Observation

@MainActor
@Observable
class WellBeingViewModel {
    
    
    var currentScreen: AppScreen = .emotionInput
    var emotionText: String = ""
    var isDarkMode: Bool = false
    
    // Questionnaire state
    var currentSectionIndex: Int = 0
    var currentQuestionIndex: Int = 0
    var selectedOptionId: Int? = nil
    var scaleValue: Int = 5
    var freeTextAnswer: String = ""
    var answers: [UserAnswer] = []
    var gridAnswers: [Int: Int] = [:]  // questionId -> selectedOptionId for allAtOnce sections
    
    // Analysis state
    enum AnalysisPhase: Equatable {
        case idle
        case connecting
        case modelStarting
        case analysing
        case failed(MotivBotAPIError)

        var isInProgress: Bool {
            switch self {
            case .connecting, .modelStarting, .analysing: return true
            case .idle, .failed: return false
            }
        }
    }

    var analysisPhase: AnalysisPhase = .idle
    var prediction: PredictionResponse? = nil
    /// The request that produced `prediction`.
    var submittedRequest: PredictionRequest? = nil

    /// Shows the support card: any answer above "not at all" on PHQ-9 item 9 (self-harm),
    /// or a likely presence of depression/hopelessness in the text.
    var elevatedRisk: Bool {
        guard let prediction, let submittedRequest else { return false }
        let depression = prediction.probability(for: .depression) ?? 0
        return submittedRequest.selfHarmItem >= 1 || depression >= PredictionResponse.presentThreshold
    }
    
    // Result / Reflection state
    var reflectionText: String = ""
    var selectedFeelingIndex: Int? = nil
    var isCompleted: Bool = false
    var showShareSheet: Bool = false
    var csvFileURL: URL? = nil
    
    // MARK: - Services
    
    private let service: PredictionServicing
    private let retryInterval: Duration
    private let maxAttempts: Int

    /// While the Space wakes up (503) or is rate limited (429), the request is retried
    /// every `retryInterval`, at most `maxAttempts` times in total.
    init(
        service: PredictionServicing? = nil,
        retryInterval: Duration = .seconds(5),
        maxAttempts: Int = 18
    ) {
        self.service = service ?? MotivBotAPIService()
        self.retryInterval = retryInterval
        self.maxAttempts = maxAttempts
    }
    
    // MARK: - Data
    
    let sections = TestData.sections
    
    let feelingEmojis: [(emoji: String, labelKey: String)] = [
        ("😢", "feeling_worse"),
        ("😐", "feeling_same"),
        ("🙂", "feeling_slightly_better"),
        ("😊", "feeling_better"),
        ("🤩", "feeling_much_better")
    ]
    
    
    var currentSection: TestSection? {
        guard currentSectionIndex < sections.count else { return nil }
        return sections[currentSectionIndex]
    }
    
    var currentQuestion: Question? {
        guard let section = currentSection,
              currentQuestionIndex < section.questions.count else { return nil }
        return section.questions[currentQuestionIndex]
    }
    
    var totalQuestions: Int {
        sections.reduce(0) { $0 + $1.questions.count }
    }
    
    var answeredQuestions: Int {
        answers.count
    }
    
    var overallProgress: Double {
        guard totalQuestions > 0 else { return 0 }
        return Double(answeredQuestions) / Double(totalQuestions)
    }
    
    var isLastQuestion: Bool {
        guard let section = currentSection else { return true }
        let isLastInSection = currentQuestionIndex >= section.questions.count - 1
        let isLastSection = currentSectionIndex >= sections.count - 1
        return isLastInSection && isLastSection
    }
    
    var canProceedEmotion: Bool {
        emotionText.trimmingCharacters(in: .whitespacesAndNewlines).count >= 10
    }
    
    var canProceedQuestion: Bool {
        guard let section = currentSection else { return false }
        
        if section.displayMode == .allAtOnce {
            // All questions in the section must be answered
            return section.questions.allSatisfy { q in gridAnswers[q.id] != nil }
        }
        
        guard let q = currentQuestion else { return false }
        switch q.type {
        case .singleChoice:
            return selectedOptionId != nil
        case .scale:
            return true
        case .freeText:
            return q.isOptional || !freeTextAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        case .integer(let range):
            return integerAnswer(in: range) != nil
        }
    }

    /// The typed number for an `.integer` question, or nil if it is not a whole number in `range`.
    func integerAnswer(in range: ClosedRange<Int>) -> Int? {
        if range == PredictionRequestBuilder.ageRange {
            return PredictionRequestBuilder.parseAge(freeTextAnswer)
        }
        let trimmed = freeTextAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let value = Int(trimmed), range.contains(value) else { return nil }
        return value
    }
    
    
    func goToScreen(_ screen: AppScreen) {
        withAnimation(.easeInOut(duration: 0.4)) {
            currentScreen = screen
        }
    }
    
    func submitEmotionAndProceed() {
        goToScreen(.questionnaire)
    }
    
    func selectOption(_ optionId: Int) {
        withAnimation(.easeInOut(duration: 0.2)) {
            selectedOptionId = optionId
        }
    }
    
    func selectGridOption(questionId: Int, optionId: Int) {
        withAnimation(.easeInOut(duration: 0.2)) {
            gridAnswers[questionId] = optionId
        }
    }
    
    func nextQuestion() {
        guard let section = currentSection else { return }
        
        if section.displayMode == .allAtOnce {
            // Save all grid answers at once
            for question in section.questions {
                if let optId = gridAnswers[question.id],
                   let opt = question.options?.first(where: { $0.id == optId }) {
                    answers.append(UserAnswer(sectionId: section.id, questionId: question.id, answerValue: .option(opt)))
                }
            }
            gridAnswers = [:]
            
            withAnimation(.easeInOut(duration: 0.35)) {
                if currentSectionIndex < sections.count - 1 {
                    currentSectionIndex += 1
                    currentQuestionIndex = 0
                } else {
                    goToScreen(.loading)
                    Task { await submitForAnalysis() }
                }
            }
            return
        }
        
        guard let question = currentQuestion else { return }
        
        let answerVal: AnswerValue
        switch question.type {
        case .singleChoice:
            guard let optionId = selectedOptionId,
                  let selectedOpt = question.options?.first(where: { $0.id == optionId }) else { return }
            answerVal = .option(selectedOpt)
        case .scale(_):
            answerVal = .scale(scaleValue)
        case .freeText:
            answerVal = .text(freeTextAnswer.trimmingCharacters(in: .whitespacesAndNewlines))
        case .integer(let range):
            guard let value = integerAnswer(in: range) else { return }
            answerVal = .text(String(value))
        }
        
        // Save the answer
        let answer = UserAnswer(
            sectionId: section.id,
            questionId: question.id,
            answerValue: answerVal
        )
        answers.append(answer)
        
        // Reset selection
        selectedOptionId = nil
        scaleValue = 5
        freeTextAnswer = ""
        
        // Advance to next question or section
        withAnimation(.easeInOut(duration: 0.35)) {
            if currentQuestionIndex < section.questions.count - 1 {
                currentQuestionIndex += 1
            } else if currentSectionIndex < sections.count - 1 {
                currentSectionIndex += 1
                currentQuestionIndex = 0
            } else {
                // All questions answered — go to loading / API
                goToScreen(.loading)
                Task {
                    await submitForAnalysis()
                }
            }
        }
    }
    
    // MARK: - API Submission
    
    func previousQuestion() {
        if currentSectionIndex == 0 && currentQuestionIndex == 0 { return }
        
        let prevSection: TestSection
        if currentQuestionIndex > 0 || (currentSection?.displayMode == .allAtOnce) {
            // Going back from an allAtOnce section or within a section
            if currentSection?.displayMode == .allAtOnce {
                // Remove any grid answers for this section
                gridAnswers = [:]
                if currentSectionIndex > 0 {
                    currentSectionIndex -= 1
                    prevSection = sections[currentSectionIndex]
                    if prevSection.displayMode == .allAtOnce {
                        // Remove all answers for the previous allAtOnce section
                        answers.removeAll { $0.sectionId == prevSection.id }
                    } else {
                        currentQuestionIndex = prevSection.questions.count - 1
                        if !answers.isEmpty { answers.removeLast() }
                    }
                }
            } else {
                if !answers.isEmpty { answers.removeLast() }
                currentQuestionIndex -= 1
            }
        } else if currentSectionIndex > 0 {
            currentSectionIndex -= 1
            prevSection = sections[currentSectionIndex]
            if prevSection.displayMode == .allAtOnce {
                answers.removeAll { $0.sectionId == prevSection.id }
                currentQuestionIndex = 0
            } else {
                currentQuestionIndex = prevSection.questions.count - 1
                if !answers.isEmpty { answers.removeLast() }
            }
        }
        
        selectedOptionId = nil
        scaleValue = 5
        freeTextAnswer = ""
    }
    
    func generateCSV() -> String {
        var csvString = "Section,Question,Answer,Score\n"
        for section in sections {
            let sectionTitle = NSLocalizedString(section.titleKey, comment: "").replacingOccurrences(of: ",", with: ";")
            for question in section.questions {
                let questionText = NSLocalizedString(question.textKey, comment: "").replacingOccurrences(of: ",", with: ";")
                if let answer = answers.first(where: { $0.questionId == question.id }) {
                    let answerText: String
                    let scoreText: String
                    switch answer.answerValue {
                    case .option(let opt):
                        answerText = NSLocalizedString(opt.textKey, comment: "").replacingOccurrences(of: ",", with: ";")
                        scoreText = "\(opt.value)"
                    case .scale(let val):
                        answerText = "\(val)"
                        scoreText = "\(val)"
                    case .text(let txt):
                        answerText = txt.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: ",", with: ";")
                        scoreText = "N/A"
                    }
                    csvString += "\(sectionTitle),\(questionText),\(answerText),\(scoreText)\n"
                }
            }
        }

        if let prediction, let request = submittedRequest {
            csvString += "\nModel input,Value\n"
            csvString += "Age,\(request.age)\n"
            csvString += "Gender,\(request.gender.rawValue)\n"
            csvString += "Gender (0 = male; 0.5 = not specified; 1 = female),\(request.gender.numericValue)\n"
            csvString += "PHQ-9 (0-27),\(request.phq9Score)\n"
            csvString += "RSES (0-30),\(request.rsesScore)\n"
            csvString += "\nDimension,Score,Level\n"
            for dimension in WellBeingDimension.allCases {
                guard let probability = prediction.probability(for: dimension),
                      let level = prediction.level(for: dimension) else { continue }
                csvString += "\(dimension.rawValue),\(String(format: "%.3f", probability)),\(level.rawValue)\n"
            }
            csvString += "elevated_risk,\(elevatedRisk)\n"
            csvString += "\nMessage category,Relevance,Message\n"
            for message in prediction.messages {
                let text = message.text.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: ",", with: ";")
                csvString += "\(message.category),\(message.score),\(text)\n"
            }
        }

        return csvString
    }
    
    // MARK: - Analysis

    /// Sends the answers to the pipeline and shows the result screen, retrying while the Space wakes up.
    /// On failure the user stays on the loading screen with a retry option.
    func submitForAnalysis() async {
        guard !analysisPhase.isInProgress else { return }
        prediction = nil
        submittedRequest = nil

        let request: PredictionRequest
        do {
            request = try PredictionRequestBuilder.build(emotionText: emotionText, answers: answers)
        } catch {
            analysisPhase = .failed(.invalidInput("\(error)"))
            return
        }

        do {
            analysisPhase = .connecting
            let response = try await predictWithRetry(request)
            submittedRequest = request
            prediction = response
            saveCSV()
            analysisPhase = .idle
            goToScreen(.result)
        } catch let error as MotivBotAPIError {
            analysisPhase = .failed(error)
        } catch {
            analysisPhase = .failed(.serverUnreachable)
        }
    }

    private func predictWithRetry(_ request: PredictionRequest) async throws -> PredictionResponse {
        for attempt in 1...max(maxAttempts, 1) {
            do {
                return try await service.predict(request)
            } catch MotivBotAPIError.modelLoading where attempt < maxAttempts {
                analysisPhase = .modelStarting
                try await Task.sleep(for: retryInterval)
            }
        }
        throw MotivBotAPIError.modelLoading
    }

    private func saveCSV() {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("SurveyAnswers.csv")
        do {
            try generateCSV().write(to: tempURL, atomically: true, encoding: .utf8)
            csvFileURL = tempURL
        } catch {
            csvFileURL = nil
        }
    }

    // MARK: - Reflection
    
    func submitReflection() {
        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
            isCompleted = true
        }
    }
    
    func restartAssessment() {
        withAnimation(.easeInOut(duration: 0.4)) {
            currentScreen = .emotionInput
            emotionText = ""
            currentSectionIndex = 0
            currentQuestionIndex = 0
            selectedOptionId = nil
            scaleValue = 5
            freeTextAnswer = ""
            answers = []
            gridAnswers = [:]
            analysisPhase = .idle
            prediction = nil
            submittedRequest = nil
            reflectionText = ""
            selectedFeelingIndex = nil
            isCompleted = false
            showShareSheet = false
            csvFileURL = nil
        }
    }
    
    // MARK: - Theme
    
    func toggleTheme() {
        withAnimation(.easeInOut(duration: 0.3)) {
            isDarkMode.toggle()
        }
    }
}
