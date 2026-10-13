package com.motivbot.app.domain.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * Port of `Models.swift`.
 *
 * The Swift models carried localization *keys* as `String` (`textKey`) and resolved them at
 * render time through `NSLocalizedString`. On Android the idiomatic equivalent is to carry the
 * resource id itself: it is compile-time checked, survives obfuscation, and removes the
 * `getIdentifier()` reflection that key-strings would otherwise force on us.
 */

/** Swift: `enum AppScreen: Int`. */
enum class AppScreen {
    EmotionInput,
    Questionnaire,
    Loading,
    Result,
}

/** Swift: `enum QuestionType: Equatable`. */
@Immutable
sealed interface QuestionType {
    data object SingleChoice : QuestionType

    /** Swift's `case scale(range: ClosedRange<Int>)` — Kotlin's `IntRange` is the direct analogue. */
    data class Scale(val range: IntRange) : QuestionType

    data object FreeText : QuestionType

    /**
     * A bounded whole number, entered on a numeric keypad and rejected unless it falls in [range].
     *
     * Added for the age question, which both the Swift original and the first Kotlin pass modelled
     * as [FreeText] — so "asdasd", "-5", "999", an emoji or a whole paragraph were all accepted and
     * shipped straight into the AI prompt and the research CSV.
     */
    data class Number(val range: IntRange) : QuestionType
}

/** Swift: `struct AnswerOption: Identifiable`. */
@Immutable
data class AnswerOption(
    val id: Int,
    @StringRes val textRes: Int,
    val value: Int,
)

/** Swift: `struct Question: Identifiable`. */
@Immutable
data class Question(
    val id: Int,
    @StringRes val textRes: Int,
    val type: QuestionType = QuestionType.SingleChoice,
    val options: List<AnswerOption>? = null,
    @DrawableRes val imageRes: Int? = null,
)

/** Swift: `enum SectionDisplayMode`. */
enum class SectionDisplayMode {
    /** One question per page, with Next/Previous. */
    Sequential,

    /** The whole section rendered as a radio-button grid on a single page. */
    AllAtOnce,
}

/** Swift: `struct TestSection: Identifiable`. */
@Immutable
data class TestSection(
    val id: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val questions: List<Question>,
    val displayMode: SectionDisplayMode = SectionDisplayMode.Sequential,
)

/** Swift: `enum AnswerValue`. */
@Immutable
sealed interface AnswerValue {
    data class Option(val option: AnswerOption) : AnswerValue
    data class Scale(val value: Int) : AnswerValue
    data class Text(val text: String) : AnswerValue

    /**
     * A validated whole number (age). Distinct from [Scale] because it carries no psychometric
     * score — the CSV writes "N/A" in the score column for it, as it does for free text.
     */
    data class Number(val value: Int) : AnswerValue
}

/** Swift: `struct UserAnswer`. */
@Immutable
data class UserAnswer(
    val sectionId: Int,
    val questionId: Int,
    val answerValue: AnswerValue,
)
