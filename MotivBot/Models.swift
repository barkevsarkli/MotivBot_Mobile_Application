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
}

struct Question: Identifiable {
    let id: Int
    let textKey: String
    let type: QuestionType
    let options: [AnswerOption]?
    let imageName: String?
    
    init(id: Int, textKey: String, type: QuestionType = .singleChoice, options: [AnswerOption]? = nil, imageName: String? = nil) {
        self.id = id
        self.textKey = textKey
        self.type = type
        self.options = options
        self.imageName = imageName
    }
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
                Question(id: 1, textKey: "qa_1", type: .freeText),
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
                    id: 3,
                    textKey: "qa_3",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "qa_3_opt1", value: 1),
                        AnswerOption(id: 2, textKey: "qa_3_opt2", value: 2),
                        AnswerOption(id: 3, textKey: "qa_3_opt3", value: 3),
                        AnswerOption(id: 4, textKey: "qa_3_opt4", value: 4),
                        AnswerOption(id: 5, textKey: "qa_3_opt5", value: 5)
                    ]
                ),
                Question(
                    id: 4,
                    textKey: "qa_4",
                    type: .singleChoice,
                    options: [
                        AnswerOption(id: 1, textKey: "qa_4_opt1", value: 1),
                        AnswerOption(id: 2, textKey: "qa_4_opt2", value: 2),
                        AnswerOption(id: 3, textKey: "qa_4_opt3", value: 3),
                        AnswerOption(id: 4, textKey: "qa_4_opt4", value: 4)
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
                Question(
                    id: 6,
                    textKey: "qa_6",
                    type: .freeText
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
        ),
        
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
        
        // SECTION F
        TestSection(
            id: 6,
            titleKey: "section_f_title",
            descriptionKey: "section_f_description",
            questions: [
                Question(id: 54, textKey: "qf_1", type: .scale(range: 1...9), imageName: "image1"),
                Question(id: 55, textKey: "qf_2", type: .scale(range: 1...9), imageName: "image2")
            ]
        )/*,
        
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

// MARK: - Motivation Cards

/// One AI-generated motivational card. Each card is anchored to a different
/// test section so the three cards address distinct aspects of the user's profile.
struct MotivationCard: Identifiable, Equatable {
    let id: Int
    let sectionCode: String
    let sectionTitle: String
    let quote: String
    let author: String?
    let message: String
}

/// Swipe direction on a motivation card. Left = disliked, right = liked.
enum SwipeDirection {
    case left
    case right

    var isLiked: Bool { self == .right }
}
