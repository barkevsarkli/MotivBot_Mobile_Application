import Foundation

// MARK: - App Navigation
enum AppScreen: Int, CaseIterable {
    case emotionInput = 0
    case questionnaire = 1
    case loading = 2
    case result = 3
}

// MARK: - Question Model
enum QuestionType: Equatable {
    case singleChoice
    case scale(range: ClosedRange<Int>)
    case freeText
    /// Whole number typed on a number pad, accepted only inside `range`.
    case integer(range: ClosedRange<Int>)
}

struct Question: Identifiable {
    let id: Int
    let textKey: String
    let type: QuestionType
    let options: [AnswerOption]?
    let imageName: String?
    /// Optional questions can be skipped with an empty answer.
    let isOptional: Bool
    /// When set, the question is asked only if the condition holds and is skipped otherwise.
    let showIf: QuestionCondition?

    init(id: Int, textKey: String, type: QuestionType = .singleChoice, options: [AnswerOption]? = nil, imageName: String? = nil, isOptional: Bool = false, showIf: QuestionCondition? = nil) {
        self.id = id
        self.textKey = textKey
        self.type = type
        self.options = options
        self.imageName = imageName
        self.isOptional = isOptional
        self.showIf = showIf
    }
}

/// Holds when the single-choice question `questionId` was answered with option `optionId`.
struct QuestionCondition {
    let questionId: Int
    let optionId: Int
}

struct AnswerOption: Identifiable {
    let id: Int
    let textKey: String
    let value: Int
}

enum SectionDisplayMode {
    case sequential
    case allAtOnce
}

struct TestSection: Identifiable {
    let id: Int
    let titleKey: String
    let descriptionKey: String
    let questions: [Question]
    let displayMode: SectionDisplayMode
    
    init(id: Int, titleKey: String, descriptionKey: String, questions: [Question], displayMode: SectionDisplayMode = .sequential) {
        self.id = id
        self.titleKey = titleKey
        self.descriptionKey = descriptionKey
        self.questions = questions
        self.displayMode = displayMode
    }
}

enum AnswerValue {
    case option(AnswerOption)
    case scale(Int)
    case text(String)
}

struct UserAnswer {
    let sectionId: Int
    let questionId: Int
    let answerValue: AnswerValue
}

// MARK: - Static Test Data
struct TestData {
    static let sections: [TestSection] = [
        // SECTION A
        TestSection(
            id: 1,
            titleKey: "section_a_title",
            descriptionKey: "section_a_description",
            questions: [
                Question(id: 1, textKey: "qa_1", type: .integer(range: PredictionRequestBuilder.ageRange)),
                Question(
                    id: 2,
                    textKey: "qa_2",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "qa_2_opt1", value: 1),
                        AnswerOption(id: 2, textKey: "qa_2_opt2", value: 2),
                        AnswerOption(id: 3, textKey: "qa_2_opt3", value: 3)
                    ]
                ),
                Question(
                    id: 5,
                    textKey: "qa_5",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "qa_5_opt1", value: 1),
                        AnswerOption(id: 2, textKey: "qa_5_opt2", value: 0)
                    ]
                ),
                // Asked, and required, only after "yes" to qa_5.
                Question(
                    id: 6,
                    textKey: "qa_6",
                    type: .freeText,
                    showIf: QuestionCondition(questionId: 5, optionId: 1)
                )
            ]
        ),
        
        // SECTION B
        TestSection(
            id: 2,
            titleKey: "section_b_title",
            descriptionKey: "section_b_description",
            questions: (1...10).map { i in
                Question(
                    id: 6 + i,
                    textKey: "qb_\(i)",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "section_b_opt1", value: 0),
                        AnswerOption(id: 2, textKey: "section_b_opt2", value: 1),
                        AnswerOption(id: 3, textKey: "section_b_opt3", value: 2),
                        AnswerOption(id: 4, textKey: "section_b_opt4", value: 3)
                    ]
                )
            }
        ),
        
        // SECTION C
        TestSection(
            id: 3,
            titleKey: "section_c_title",
            descriptionKey: "section_c_description",
            questions: (1...7).map { i in
                Question(
                    id: 16 + i,
                    textKey: "qc_\(i)",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "section_c_opt1", value: 0),
                        AnswerOption(id: 2, textKey: "section_c_opt2", value: 1),
                        AnswerOption(id: 3, textKey: "section_c_opt3", value: 2),
                        AnswerOption(id: 4, textKey: "section_c_opt4", value: 3)
                    ]
                )
            }
        ),
        
        // SECTION D
        TestSection(
            id: 4,
            titleKey: "section_d_title",
            descriptionKey: "section_d_description",
            questions: (1...10).map { i in
                Question(
                    id: 23 + i,
                    textKey: "qd_\(i)",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "section_d_opt1", value: 3),
                        AnswerOption(id: 2, textKey: "section_d_opt2", value: 2),
                        AnswerOption(id: 3, textKey: "section_d_opt3", value: 1),
                        AnswerOption(id: 4, textKey: "section_d_opt4", value: 0)
                    ]
                )
            }
        )

        // SECTION E
        /*TestSection(
            id: 5,
            titleKey: "section_e_title",
            descriptionKey: "section_e_description",
            questions: (1...20).map { i in
                Question(
                    id: 33 + i,
                    textKey: "qe_\(i)",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "section_e_opt1", value: 1),
                        AnswerOption(id: 2, textKey: "section_e_opt2", value: 2),
                        AnswerOption(id: 3, textKey: "section_e_opt3", value: 3),
                        AnswerOption(id: 4, textKey: "section_e_opt4", value: 4),
                        AnswerOption(id: 5, textKey: "section_e_opt5", value: 5)
                    ]
                )
            },
            displayMode: .allAtOnce
        ),*/
        /*,
        
        // SECTION G
        TestSection(
            id: 7,
            titleKey: "section_g_title",
            descriptionKey: "section_g_description",
            questions: (1...7).map { i in
                Question(
                    id: 55 + i,
                    textKey: "qg_\(i)",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "section_g_opt1", value: 1),
                        AnswerOption(id: 2, textKey: "section_g_opt2", value: 2),
                        AnswerOption(id: 3, textKey: "section_g_opt3", value: 3),
                        AnswerOption(id: 4, textKey: "section_g_opt4", value: 4),
                        AnswerOption(id: 5, textKey: "section_g_opt5", value: 5),
                        AnswerOption(id: 6, textKey: "section_g_opt6", value: 6),
                        AnswerOption(id: 7, textKey: "section_g_opt7", value: 7)
                    ]
                )
          
            }
        )
        */
    ]
}

// MARK: - Model Result Dimensions

/// The five heads of the XLM-R classifier, in model order.
enum WellBeingDimension: String, CaseIterable, Identifiable {
    case anxiety = "Anxiety_Fear_Worry"
    case depression = "Depression_Hopelessness_Sadness"
    case selfEsteem = "Self_Esteem_Issue"
    case positiveAffect = "Positive_Affect"
    case negativeAffect = "Negative_Affect"

    var id: String { rawValue }

    /// Positive affect is a strength (High = good). The other four are concerns (High = more difficulty).
    var isPositiveTrait: Bool { self == .positiveAffect }

    var iconName: String {
        switch self {
        case .anxiety: return "cloud.drizzle"
        case .depression: return "sun.haze"
        case .selfEsteem: return "person.crop.circle.badge.checkmark"
        case .positiveAffect: return "sparkles"
        case .negativeAffect: return "wind"
        }
    }

    private var keyStem: String {
        switch self {
        case .anxiety: return "dim_anxiety"
        case .depression: return "dim_mood"
        case .selfEsteem: return "dim_self_esteem"
        case .positiveAffect: return "dim_positive"
        case .negativeAffect: return "dim_tension"
        }
    }

    var titleKey: String { "\(keyStem)_title" }

    func messageKey(for level: PredictionResponse.Level) -> String {
        switch level {
        case .no: return "\(keyStem)_no"
        case .low: return "\(keyStem)_low"
        case .high: return "\(keyStem)_high"
        }
    }

    /// How the level should feel to the user, independent of the dimension's direction.
    enum Tone {
        case steady
        case gentle
        case care
    }

    func tone(for level: PredictionResponse.Level) -> Tone {
        switch (isPositiveTrait, level) {
        case (false, .no), (true, .high): return .steady
        case (_, .low): return .gentle
        case (false, .high), (true, .no): return .care
        }
    }
}
