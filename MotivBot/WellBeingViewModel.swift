import Foundation
import SwiftUI
import Observation

@MainActor
@Observable
class WellBeingViewModel {
    
    
    var currentScreen: AppScreen = .emotionInput
    /// Starting to type locks the language; clearing the text on the first screen unlocks it again.
    var emotionText: String = "" {
        didSet {
            guard emotionText != oldValue else { return }
            updateLanguageLock()
            scheduleSave()
        }
    }
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
        /// The Space is starting; this can take about a minute.
        case modelWakingUp
        case analysing
        case failed(AnalysisError)

        var isInProgress: Bool {
            switch self {
            case .connecting, .modelWakingUp, .analysing: return true
            case .idle, .failed: return false
            }
        }
    }

    var analysisPhase: AnalysisPhase = .idle
    var prediction: PredictionResponse? = nil
    /// The request that produced `prediction`. Not restored after relaunch.
    var submittedRequest: PredictionRequest? = nil
    /// When `prediction` arrived.
    private(set) var resultDate: Date? = nil

    /// Shows the support card: any answer above "not at all" on PHQ-9 item 9 (self-harm),
    /// or a likely presence of depression/hopelessness in the text.
    private(set) var elevatedRisk: Bool = false

    static func isElevatedRisk(_ prediction: PredictionResponse, request: PredictionRequest) -> Bool {
        let depression = prediction.probability(for: .depression) ?? 0
        return request.selfHarmItem >= 1 || depression >= PredictionResponse.presentThreshold
    }

    /// The result for PDF / image export, or nil before a result exists.
    var resultSummary: ResultSummary? {
        guard let prediction, let resultDate else { return nil }
        return ResultSummary(prediction: prediction, date: resultDate, language: localization.currentLanguage, elevatedRisk: elevatedRisk)
    }

    #if DEBUG
    /// The raw error behind the last `.failed` phase. Debug builds only.
    @ObservationIgnored private(set) var debugLastError: Error?
    #endif

    // Language
    /// The language can change only on the first screen, before the assessment starts.
    /// The UI should show the language toggle only while this is true.
    var canChangeLanguage: Bool {
        currentScreen == .emotionInput && !localization.isLocked && resumableSession == nil
    }

    // Save & resume
    /// A saved, unfinished assessment found at launch. Offer `continueSession()` or `startOver()`.
    private(set) var resumableSession: ResumableSession? = nil
    
    // Result / Reflection state
    var reflectionText: String = ""
    var selectedFeelingIndex: Int? = nil
    var isCompleted: Bool = false
    var showShareSheet: Bool = false
    var csvFileURL: URL? = nil
    
    // MARK: - Services
    
    private let service: PredictionServicing
    let localization: LocalizationManager
    private let store: SessionStoring
    private let saveDelay: Duration
    private let now: () -> Date

    @ObservationIgnored private var pendingSession: (session: SavedSession, answers: [UserAnswer])?
    @ObservationIgnored private var saveTask: Task<Void, Never>?
    @ObservationIgnored private var analysisTask: Task<PredictionResponse, Error>?

    /// Restores a saved session (as `resumableSession`) or the last result on creation.
    /// Retries while the Space wakes up are handled by the service.
    init(
        service: PredictionServicing? = nil,
        localization: LocalizationManager? = nil,
        store: SessionStoring? = nil,
        saveDelay: Duration = .milliseconds(500),
        now: @escaping () -> Date = Date.init
    ) {
        self.service = service ?? MotivBotAPIService()
        self.localization = localization ?? LocalizationManager()
        self.store = store ?? FileSessionStore()
        self.saveDelay = saveDelay
        self.now = now
        restoreSavedState()
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
    
    enum EmotionTextStatus: Equatable {
        case tooShort
        case tooLong
        /// The pipeline would read the text as another language than the app's.
        case wrongLanguage
        case valid
    }

    var emotionTextStatus: EmotionTextStatus {
        let text = emotionText.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.count < PredictionRequest.textLengthRange.lowerBound { return .tooShort }
        if text.count > PredictionRequest.textLengthRange.upperBound { return .tooLong }
        if PipelineLanguage.detect(text) != localization.currentLanguage.pipelineLanguage { return .wrongLanguage }
        return .valid
    }

    /// Localization key of the warning under the emotion text, or nil when there is nothing to say.
    /// `emotion_too_long` takes the maximum length as `%d`.
    var emotionTextWarningKey: String? {
        switch emotionTextStatus {
        case .wrongLanguage: return "emotion_wrong_language"
        case .tooLong: return "emotion_too_long"
        case .tooShort, .valid: return nil
        }
    }

    var canProceedEmotion: Bool {
        emotionTextStatus == .valid
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
        guard canProceedEmotion else { return }
        localization.lock()
        goToScreen(.questionnaire)
        scheduleSave()
        // The questionnaire takes minutes; wake the Space now.
        prewarmService()
    }

    /// Pings the Space in the background. Call at launch; safe to call repeatedly.
    func prewarmService() {
        let service = self.service
        Task { await service.prewarm() }
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
            scheduleSave()
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
        scheduleSave()
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
        scheduleSave()
    }
    
    func generateCSV() -> String {
        var csvString = "Section,Question,Answer,Score\n"
        for section in sections {
            let sectionTitle = localization.localizedString(section.titleKey).replacingOccurrences(of: ",", with: ";")
            for question in section.questions {
                let questionText = localization.localizedString(question.textKey).replacingOccurrences(of: ",", with: ";")
                if let answer = answers.first(where: { $0.questionId == question.id }) {
                    let answerText: String
                    let scoreText: String
                    switch answer.answerValue {
                    case .option(let opt):
                        answerText = localization.localizedString(opt.textKey).replacingOccurrences(of: ",", with: ";")
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
            csvString += "RSES sent to model (10-40),\(request.pipelineRSESScore)\n"
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

    /// Sends the answers to the pipeline and shows the result screen. The service retries while the Space wakes up.
    /// On failure the user stays on the loading screen with a retry option.
    func submitForAnalysis() async {
        guard !analysisPhase.isInProgress else { return }
        prediction = nil
        submittedRequest = nil

        let request: PredictionRequest
        do {
            request = try PredictionRequestBuilder.build(emotionText: emotionText, answers: answers)
            try request.validate()
        } catch {
            fail(.unexpected, raw: error)
            return
        }

        analysisPhase = .connecting
        let service = self.service
        let task = Task { [weak self] in
            try await service.predict(request) { progress in
                self?.apply(progress)
            }
        }
        analysisTask = task

        do {
            let response = try await task.value
            guard !task.isCancelled else { return }
            analysisTask = nil
            showResult(response, for: request)
        } catch is CancellationError {
            analysisPhase = .idle
        } catch let error as MotivBotAPIError {
            analysisTask = nil
            fail(error.analysisError, raw: error)
        } catch {
            analysisTask = nil
            fail(.unexpected, raw: error)
        }
    }

    private func apply(_ progress: PredictionProgress) {
        guard analysisPhase.isInProgress else { return }
        switch progress {
        case .connecting: analysisPhase = .connecting
        case .wakingUp: analysisPhase = .modelWakingUp
        case .analysing: analysisPhase = .analysing
        }
    }

    private func fail(_ error: AnalysisError, raw: Error) {
        #if DEBUG
        debugLastError = raw
        #endif
        analysisPhase = .failed(error)
    }

    private func showResult(_ response: PredictionResponse, for request: PredictionRequest) {
        submittedRequest = request
        prediction = response
        resultDate = now()
        elevatedRisk = Self.isElevatedRisk(response, request: request)
        saveCSV()
        analysisPhase = .idle

        saveTask?.cancel()
        store.deleteSession()
        store.saveResult(SavedResult(
            language: localization.currentLanguage,
            completedAt: resultDate ?? now(),
            prediction: response,
            elevatedRisk: elevatedRisk
        ))
        goToScreen(.result)
    }

    private func saveCSV() {
        let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent("SurveyAnswers.csv")
        do {
            try Data(generateCSV().utf8).write(to: tempURL, options: [.atomic, .completeFileProtection])
            csvFileURL = tempURL
        } catch {
            csvFileURL = nil
        }
    }

    // MARK: - Save & resume

    /// Writes the pending save now. Call when the app leaves the foreground.
    func flushPendingSave() {
        guard saveTask != nil else { return }
        saveTask?.cancel()
        saveTask = nil
        saveSessionNow()
    }

    /// Restores the saved session: language (locked), text, answers and position.
    func continueSession() {
        guard let pending = pendingSession else { return }
        pendingSession = nil
        resumableSession = nil

        let session = pending.session
        sessionStartedAt = session.startedAt
        localization.lock(to: session.language)
        emotionText = session.emotionText
        answers = pending.answers
        currentSectionIndex = session.sectionIndex
        currentQuestionIndex = session.questionIndex
        switch session.stage {
        case .emotionInput:
            currentScreen = .emotionInput
        case .questionnaire:
            currentScreen = .questionnaire
            prewarmService()
        case .analysis:
            currentScreen = .loading
            Task { await submitForAnalysis() }
        }
    }

    /// Discards the saved session and stays on the first screen.
    func startOver() {
        pendingSession = nil
        resumableSession = nil
        store.deleteSession()
        localization.unlock()
    }

    private func restoreSavedState() {
        if let session = store.loadSession() {
            let restored = session.answers.compactMap { UserAnswer($0, sections: sections) }
            let positionIsValid = sections.indices.contains(session.sectionIndex)
                && sections[session.sectionIndex].questions.indices.contains(session.questionIndex)
            if restored.count == session.answers.count, positionIsValid {
                pendingSession = (session, restored)
                resumableSession = ResumableSession(
                    language: session.language,
                    startedAt: session.startedAt,
                    answeredQuestions: restored.count,
                    totalQuestions: totalQuestions
                )
                return
            }
            store.deleteSession()
        }

        if let result = store.loadResult() {
            localization.lock(to: result.language)
            prediction = result.prediction
            resultDate = result.completedAt
            elevatedRisk = result.elevatedRisk
            currentScreen = .result
        }
    }

    private func updateLanguageLock() {
        guard currentScreen == .emotionInput, answers.isEmpty, pendingSession == nil else { return }
        if emotionText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            localization.unlock()
        } else {
            localization.lock()
        }
    }

    @ObservationIgnored private var sessionStartedAt: Date?

    private func scheduleSave() {
        guard pendingSession == nil, prediction == nil else { return }
        saveTask?.cancel()
        let delay = saveDelay
        saveTask = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            self?.saveTask = nil
            self?.saveSessionNow()
        }
    }

    private func saveSessionNow() {
        guard pendingSession == nil, prediction == nil else { return }
        if emotionText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && answers.isEmpty {
            sessionStartedAt = nil
            store.deleteSession()
            return
        }
        let startedAt = sessionStartedAt ?? now()
        sessionStartedAt = startedAt

        let stage: SavedSession.Stage
        switch currentScreen {
        case .emotionInput: stage = .emotionInput
        case .questionnaire: stage = .questionnaire
        case .loading, .result: stage = .analysis
        }
        store.saveSession(SavedSession(
            language: localization.currentLanguage,
            emotionText: emotionText,
            answers: answers.map(\.saved),
            stage: stage,
            sectionIndex: currentSectionIndex,
            questionIndex: currentQuestionIndex,
            startedAt: startedAt
        ))
    }

    // MARK: - Reflection
    
    func submitReflection() {
        withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
            isCompleted = true
        }
    }
    
    /// Cancels any analysis and deletes the saved session, the last result and the CSV. Unlocks the language.
    func restartAssessment() {
        analysisTask?.cancel()
        analysisTask = nil
        saveTask?.cancel()
        saveTask = nil
        pendingSession = nil
        resumableSession = nil
        sessionStartedAt = nil
        store.deleteSession()
        store.deleteResult()
        if let csvFileURL {
            try? FileManager.default.removeItem(at: csvFileURL)
        }

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
            resultDate = nil
            elevatedRisk = false
            reflectionText = ""
            selectedFeelingIndex = nil
            isCompleted = false
            showShareSheet = false
            csvFileURL = nil
        }
        localization.unlock()
    }
    
    // MARK: - Theme
    
    func toggleTheme() {
        withAnimation(.easeInOut(duration: 0.3)) {
            isDarkMode.toggle()
        }
    }
}

/// A saved, unfinished assessment the UI can offer to continue.
struct ResumableSession: Equatable {
    let language: AppLanguage
    let startedAt: Date
    let answeredQuestions: Int
    let totalQuestions: Int
}
