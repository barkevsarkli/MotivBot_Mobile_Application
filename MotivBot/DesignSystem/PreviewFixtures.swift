#if DEBUG
import SwiftUI

/// Sample data and a host view for SwiftUI previews. Uses an in-memory store and a fake service,
/// so previews never touch the network or the files on disk.
enum PreviewFixtures {

    static let prediction = PredictionResponse(
        emotions: [
            WellBeingDimension.anxiety.rawValue: 0.62,
            WellBeingDimension.depression.rawValue: 0.31,
            WellBeingDimension.selfEsteem.rawValue: 0.12,
            WellBeingDimension.positiveAffect.rawValue: 0.55,
            WellBeingDimension.negativeAffect.rawValue: 0.28,
        ],
        messages: [
            .init(text: "Bu kadar yoğun hissetmeniz çok anlaşılır. Duygularınıza alan tanımak bir güç göstergesidir.", category: "Emotional Validation", score: 0.91, author: nil),
            .init(text: "Bugün zor geçse de, bu his kalıcı değil. Her gün yeni bir başlangıç olabilir.", category: "Cognitive Reframing", score: 0.84, author: nil),
            .init(text: "Kendinize on dakikalık sakin bir yürüyüş hediye etmeyi deneyebilirsiniz.", category: "Action-Oriented Encouragement", score: 0.78, author: nil),
        ]
    )

    static let persianPrediction = PredictionResponse(
        emotions: prediction.emotions,
        messages: [
            .init(text: "کاملاً قابل درک است که این‌قدر احساس سنگینی می‌کنید. فضا دادن به احساساتتان نشانهٔ قدرت است.", category: "Emotional Validation", score: 0.91, author: nil),
            .init(text: "حتی اگر امروز سخت گذشت، این احساس همیشگی نیست.", category: "Cognitive Reframing", score: 0.84, author: nil),
            .init(text: "شاید یک پیاده‌روی آرام ده‌دقیقه‌ای برای خودتان هدیه باشد.", category: "Action-Oriented Encouragement", score: 0.78, author: nil),
        ]
    )

    static let sampleText = "Bu hafta biraz yorgun hissediyorum ama yine de bir şeyler yapmaya çalışıyorum. Ailem yanımda ve bu bana iyi geliyor."
    static let samplePersianText = "این هفته کمی خسته‌ام اما سعی می‌کنم کارهایم را انجام دهم. خانواده‌ام کنارم هستند و این حالم را بهتر می‌کند."

    /// Answers to the first `count` questions, in order, as they would be saved.
    static func savedAnswers(count: Int) -> [SavedAnswer] {
        let questions = TestData.sections.flatMap { section in section.questions.map { (section.id, $0) } }
        return questions.prefix(count).map { sectionId, question in
            let value: SavedAnswer.Value
            switch question.type {
            case .integer: value = .text("29")
            case .freeText: value = .text("")
            case .scale(let range): value = .scale(range.lowerBound)
            case .singleChoice: value = .option(id: question.options?.first?.id ?? 1)
            }
            return SavedAnswer(sectionId: sectionId, questionId: question.id, value: value)
        }
    }

    /// The position after answering `count` questions.
    static func position(afterAnswering count: Int) -> (section: Int, question: Int) {
        var remaining = count
        for (index, section) in TestData.sections.enumerated() {
            if remaining < section.questions.count { return (index, remaining) }
            remaining -= section.questions.count
        }
        return (TestData.sections.count - 1, 0)
    }
}

/// A session store that keeps everything in memory.
final class PreviewSessionStore: SessionStoring {
    var session: SavedSession?
    var result: SavedResult?

    init(session: SavedSession? = nil, result: SavedResult? = nil) {
        self.session = session
        self.result = result
    }

    func loadSession() -> SavedSession? { session }
    func saveSession(_ session: SavedSession) { self.session = session }
    func deleteSession() { session = nil }
    func loadResult() -> SavedResult? { result }
    func saveResult(_ result: SavedResult) { self.result = result }
    func deleteResult() { result = nil }
}

/// A service that answers after a short pause, or fails when asked to.
final class PreviewPredictionService: PredictionServicing {
    let failure: MotivBotAPIError?

    init(failure: MotivBotAPIError? = nil) {
        self.failure = failure
    }

    func predict(_ request: PredictionRequest, progress: @escaping (PredictionProgress) -> Void) async throws -> PredictionResponse {
        try await Task.sleep(for: .seconds(2))
        if let failure { throw failure }
        return PreviewFixtures.prediction
    }

    func prewarm() async {}
}

/// The app state a preview starts in.
enum PreviewState {
    case fresh
    case resumable(answered: Int)
    case emotionInput(text: String?)
    case questionnaire(answered: Int)
    case loading(WellBeingViewModel.AnalysisPhase)
    case result(elevatedRisk: Bool)
    case completed
}

/// Wraps a screen with a view model in `state`, the app background, the language and its layout direction.
struct PreviewHost<Content: View>: View {
    @State private var localization: LocalizationManager
    @State private var viewModel: WellBeingViewModel
    private let content: Content

    init(_ state: PreviewState = .fresh, language: AppLanguage = .turkish, @ViewBuilder content: () -> Content) {
        let localization = LocalizationManager()
        localization.setLanguage(language)
        let store = PreviewSessionStore()
        let now = Date()

        switch state {
        case .resumable(let answered), .questionnaire(let answered):
            let position = PreviewFixtures.position(afterAnswering: answered)
            store.session = SavedSession(
                language: language,
                emotionText: language == .persian ? PreviewFixtures.samplePersianText : PreviewFixtures.sampleText,
                answers: PreviewFixtures.savedAnswers(count: answered),
                stage: .questionnaire,
                sectionIndex: position.section,
                questionIndex: position.question,
                startedAt: now.addingTimeInterval(-2 * 60 * 60)
            )
        case .result, .completed:
            let elevated: Bool
            if case .result(let risk) = state { elevated = risk } else { elevated = false }
            store.result = SavedResult(
                language: language,
                completedAt: now,
                prediction: language == .persian ? PreviewFixtures.persianPrediction : PreviewFixtures.prediction,
                elevatedRisk: elevated
            )
        case .fresh, .emotionInput, .loading:
            break
        }

        let viewModel = WellBeingViewModel(
            service: PreviewPredictionService(),
            localization: localization,
            store: store,
            saveDelay: .seconds(3600)
        )

        switch state {
        case .questionnaire:
            viewModel.continueSession()
        case .emotionInput(let text):
            viewModel.currentScreen = .emotionInput
            if let text { viewModel.emotionText = text }
        case .loading(let phase):
            viewModel.currentScreen = .loading
            viewModel.analysisPhase = phase
        case .completed:
            viewModel.isCompleted = true
        case .fresh, .resumable, .result:
            break
        }

        _localization = State(initialValue: localization)
        _viewModel = State(initialValue: viewModel)
        self.content = content()
    }

    var body: some View {
        ZStack {
            AppBackground()
            content
        }
        .environment(viewModel)
        .environment(localization)
        .environment(\.layoutDirection, localization.layoutDirection)
        .environment(\.locale, localization.locale)
    }
}
#endif
