package com.motivbot.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.motivbot.app.R
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.domain.model.AppScreen
import com.motivbot.app.domain.model.Question
import com.motivbot.app.domain.model.QuestionType
import com.motivbot.app.domain.model.SectionDisplayMode
import com.motivbot.app.domain.model.TestData
import com.motivbot.app.domain.model.TestSection
import com.motivbot.app.domain.model.UserAnswer

/** Swift: the `feelingEmojis` tuple array on the ViewModel. */
@Immutable
data class Feeling(val emoji: String, @StringRes val labelRes: Int)

val FeelingEmojis: List<Feeling> = listOf(
    Feeling("😢", R.string.feeling_worse),
    Feeling("😐", R.string.feeling_same),
    Feeling("🙂", R.string.feeling_slightly_better),
    Feeling("😊", R.string.feeling_better),
    Feeling("🤩", R.string.feeling_much_better),
)

/** An error the UI should surface, kept as a resource + args so it localizes with the app. */
@Immutable
data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList())

/**
 * A single immutable snapshot of everything the screens render.
 *
 * The Swift ViewModel exposed ~18 independent `var`s through `@Observable`, which meant any
 * combination of them could exist — including impossible ones (e.g. `isLoading` while on the
 * result screen). Collapsing them into one `data class` behind a `StateFlow` gives every emission
 * a consistent, copy-on-write snapshot, and makes the derived values below single-sourced.
 */
@Immutable
data class WellBeingUiState(
    val currentScreen: AppScreen = AppScreen.EmotionInput,
    val emotionText: String = "",
    val isDarkMode: Boolean = false,
    val language: AppLanguage = AppLanguage.Default,

    // Questionnaire state
    val currentSectionIndex: Int = 0,
    val currentQuestionIndex: Int = 0,
    val selectedOptionId: Int? = null,
    val scaleValue: Int = DEFAULT_SCALE_VALUE,
    val freeTextAnswer: String = "",
    /** Raw digits for a [QuestionType.Number] question; kept as text so it can be shown mid-edit. */
    val numberAnswer: String = "",
    val answers: List<UserAnswer> = emptyList(),
    /** questionId -> selectedOptionId, for `AllAtOnce` sections. Swift: `gridAnswers`. */
    val gridAnswers: Map<Int, Int> = emptyMap(),

    // API state
    val isLoading: Boolean = false,
    val aiResponse: String = "",
    val errorMessage: UiMessage? = null,

    // Result / reflection state
    val reflectionText: String = "",
    val selectedFeelingIndex: Int? = null,
    val isCompleted: Boolean = false,
    val csvUri: String? = null,
    val shouldOfferShare: Boolean = false,
) {

    val sections: List<TestSection> get() = TestData.sections

    /** Swift: `var currentSection: TestSection?`. */
    val currentSection: TestSection? get() = sections.getOrNull(currentSectionIndex)

    /** Swift: `var currentQuestion: Question?`. */
    val currentQuestion: Question?
        get() = currentSection?.questions?.getOrNull(currentQuestionIndex)

    /** Swift: `var totalQuestions: Int`. */
    val totalQuestions: Int get() = TestData.totalQuestions

    /** Swift: `var answeredQuestions: Int`. */
    val answeredQuestions: Int get() = answers.size

    /** Swift: `var overallProgress: Double`. */
    val overallProgress: Float
        get() = if (totalQuestions == 0) 0f else answeredQuestions.toFloat() / totalQuestions

    /** Swift: `var isLastQuestion: Bool`. */
    val isLastQuestion: Boolean
        get() {
            val section = currentSection ?: return true
            val isLastInSection = currentQuestionIndex >= section.questions.lastIndex
            val isLastSection = currentSectionIndex >= sections.lastIndex
            return isLastInSection && isLastSection
        }

    /** Swift: `var canProceedEmotion: Bool` — at least 10 non-whitespace characters. */
    val canProceedEmotion: Boolean
        get() = emotionText.trim().length >= MIN_EMOTION_LENGTH

    /** Swift: `var canProceedQuestion: Bool`. */
    val canProceedQuestion: Boolean
        get() {
            val section = currentSection ?: return false

            if (section.displayMode == SectionDisplayMode.AllAtOnce) {
                // Every question in the grid must have a selection before moving on.
                return section.questions.all { gridAnswers.containsKey(it.id) }
            }

            return when (val type = currentQuestion?.type) {
                is QuestionType.SingleChoice -> selectedOptionId != null
                is QuestionType.Scale -> true
                is QuestionType.FreeText -> freeTextAnswer.isNotBlank()
                is QuestionType.Number -> numberAnswer.toIntOrNull() in type.range
                null -> false
            }
        }

    /**
     * The parsed number, or null while the field is empty or out of range.
     *
     * Note `"".toIntOrNull()` is null and `null in range` is false, so an empty field simply blocks
     * progress rather than throwing.
     */
    val numberAnswerValue: Int?
        get() = (currentQuestion?.type as? QuestionType.Number)
            ?.let { type -> numberAnswer.toIntOrNull()?.takeIf { it in type.range } }

    /** True once the user has typed something that is not yet a valid value — drives the inline error. */
    val showNumberError: Boolean
        get() = numberAnswer.isNotEmpty() && numberAnswerValue == null

    /** Whether the "previous" affordance should be shown at all. Swift did this inline in the view. */
    val canGoBack: Boolean
        get() = currentSectionIndex > 0 || currentQuestionIndex > 0

    companion object {
        const val DEFAULT_SCALE_VALUE = 5
        const val MIN_EMOTION_LENGTH = 10
    }
}
