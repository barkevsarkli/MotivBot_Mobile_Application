package com.motivbot.app.domain.usecase

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.StringProvider
import com.motivbot.app.domain.model.AnswerValue
import com.motivbot.app.domain.model.TestData
import com.motivbot.app.domain.model.UserAnswer
import javax.inject.Inject

/** A fully resolved answer row — the unit both the database and the CSV export deal in. */
data class AnswerRow(
    val sectionId: Int,
    val questionId: Int,
    val sectionTitle: String,
    val questionText: String,
    val answerText: String,
    val score: Int?,
)

/**
 * Turns in-memory [UserAnswer]s (which reference string *resources*) into [AnswerRow]s carrying
 * resolved text, in the language the user answered in.
 *
 * This is the seam the Swift code lacked: `generateCSV()` and `buildPrompt()` each re-resolved
 * every localization key themselves, duplicating the section/question walk. Both now share it.
 */
class AnswerRowMapper @Inject constructor(
    private val stringProvider: StringProvider,
) {

    operator fun invoke(answers: List<UserAnswer>, language: AppLanguage): List<AnswerRow> {
        val answersByQuestion = answers.associateBy(UserAnswer::questionId)

        return TestData.sections.flatMap { section ->
            val sectionTitle = stringProvider.getString(language, section.titleRes)

            section.questions.mapNotNull { question ->
                val answer = answersByQuestion[question.id] ?: return@mapNotNull null

                val (answerText, score) = when (val value = answer.answerValue) {
                    is AnswerValue.Option ->
                        stringProvider.getString(language, value.option.textRes) to value.option.value

                    is AnswerValue.Scale -> value.value.toString() to value.value

                    is AnswerValue.Text -> value.text to null

                    // Age is data, not a score — the CSV's Score column stays "N/A".
                    is AnswerValue.Number -> value.value.toString() to null
                }

                AnswerRow(
                    sectionId = section.id,
                    questionId = question.id,
                    sectionTitle = sectionTitle,
                    questionText = stringProvider.getString(language, question.textRes),
                    answerText = answerText,
                    score = score,
                )
            }
        }
    }
}
