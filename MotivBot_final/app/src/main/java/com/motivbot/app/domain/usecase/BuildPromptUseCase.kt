package com.motivbot.app.domain.usecase

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.StringProvider
import com.motivbot.app.domain.model.AnswerValue
import com.motivbot.app.domain.model.TestData
import com.motivbot.app.domain.model.UserAnswer
import javax.inject.Inject

/**
 * Port of `WellBeingViewModel.buildPrompt()`.
 *
 * Pulled out of the ViewModel because it is pure string assembly over inputs — which makes it
 * trivially unit-testable, unlike the Swift version that could only be exercised through the
 * whole `@MainActor` object.
 *
 * Answers are indexed by question id once (`associateBy`) instead of the Swift
 * `answers.first(where:)` inside the question loop, turning an O(questions × answers) scan into
 * O(questions + answers).
 */
class BuildPromptUseCase @Inject constructor(
    private val stringProvider: StringProvider,
) {

    operator fun invoke(
        emotionText: String,
        answers: List<UserAnswer>,
        language: AppLanguage,
    ): String {
        val answersByQuestion = answers.associateBy(UserAnswer::questionId)

        return buildString {
            appendLine("The user expressed their current emotions as follows:")
            appendLine("\"$emotionText\"")
            appendLine()
            appendLine("They also completed a psychological well-being questionnaire. Here are the results:")
            appendLine()

            TestData.sections.forEach { section ->
                appendLine("--- ${stringProvider.getString(language, section.titleRes)} ---")

                section.questions.forEach { question ->
                    val answer = answersByQuestion[question.id] ?: return@forEach
                    val questionText = stringProvider.getString(language, question.textRes)

                    appendLine("Q: $questionText")
                    when (val value = answer.answerValue) {
                        is AnswerValue.Option -> {
                            val optionText = stringProvider.getString(language, value.option.textRes)
                            appendLine("A: $optionText (Score: ${value.option.value})")
                        }

                        is AnswerValue.Scale -> appendLine("A: ${value.value} (Scale value)")

                        is AnswerValue.Text -> appendLine("A: ${value.text} (Free text response)")

                        is AnswerValue.Number -> appendLine("A: ${value.value}")
                    }
                    appendLine()
                }
            }

            append(
                "Based on all of the above, please provide a personalized motivational quote " +
                    "and a brief supportive message.",
            )
        }
    }
}
