import Foundation

/// Maps the questionnaire answers to a `PredictionRequest`. Pure: no state, no networking.
enum PredictionRequestBuilder {

    enum BuildError: Error, Equatable {
        case emptyText
        case missingAnswer(questionId: Int)
        case invalidAge
    }

    // Question ids from TestData.sections.
    static let ageQuestionId = 1
    static let genderQuestionId = 2
    /// qb_1 ... qb_9. qb_10 (id 16) is an attention check and is never sent.
    static let phq9QuestionIds = Array(7...15)
    static let attentionCheckQuestionId = 16
    /// qd_1 ... qd_10, sent as raw option values. The server applies the reverse scoring.
    static let rsesQuestionIds = Array(24...33)

    static let ageRange = 10...100

    static func build(emotionText: String, answers: [UserAnswer], languageCode: String?) throws -> PredictionRequest {
        let text = emotionText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw BuildError.emptyText }

        guard case .text(let ageText)? = answer(ageQuestionId, in: answers) else {
            throw BuildError.missingAnswer(questionId: ageQuestionId)
        }
        guard let age = parseAge(ageText) else { throw BuildError.invalidAge }

        return PredictionRequest(
            text: text,
            age: age,
            gender: try gender(from: answers),
            phq9Items: try optionValues(phq9QuestionIds, in: answers),
            rsesItems: try optionValues(rsesQuestionIds, in: answers),
            language: languageCode
        )
    }

    /// Parses an age typed by the user. Accepts any Unicode digits (e.g. Persian "۲۵").
    /// Returns nil unless the value is a whole number in `ageRange`.
    static func parseAge(_ text: String) -> Int? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 3 else { return nil }
        var value = 0
        for character in trimmed {
            guard character.isNumber, let digit = character.wholeNumberValue else { return nil }
            value = value * 10 + digit
        }
        return ageRange.contains(value) ? value : nil
    }

    private static func gender(from answers: [UserAnswer]) throws -> PredictionRequest.Gender {
        guard case .option(let option)? = answer(genderQuestionId, in: answers) else {
            throw BuildError.missingAnswer(questionId: genderQuestionId)
        }
        switch option.value {
        case 1: return .male
        case 2: return .female
        default: return .unspecified
        }
    }

    private static func optionValues(_ questionIds: [Int], in answers: [UserAnswer]) throws -> [Int] {
        try questionIds.map { id in
            guard case .option(let option)? = answer(id, in: answers) else {
                throw BuildError.missingAnswer(questionId: id)
            }
            return option.value
        }
    }

    private static func answer(_ questionId: Int, in answers: [UserAnswer]) -> AnswerValue? {
        answers.last(where: { $0.questionId == questionId })?.answerValue
    }
}
