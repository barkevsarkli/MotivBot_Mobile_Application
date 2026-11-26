package com.motivbot.app.domain

import androidx.annotation.StringRes
import com.motivbot.app.R

// Port of Models.swift: navigation, questionnaire model and the static test data.

enum class AppScreen { EmotionInput, Questionnaire, Loading, Result }

sealed interface QuestionType {
    data object SingleChoice : QuestionType
    data class Scale(val range: IntRange) : QuestionType
    data object FreeText : QuestionType

    /** Whole number typed on a number pad, accepted only inside [range]. */
    data class IntegerInput(val range: IntRange) : QuestionType
}

/** Holds when the single-choice question [questionId] was answered with option [optionId]. */
data class QuestionCondition(val questionId: Int, val optionId: Int)

data class AnswerOption(val id: Int, @StringRes val textRes: Int, val value: Int)

data class Question(
    val id: Int,
    @StringRes val textRes: Int,
    val type: QuestionType = QuestionType.SingleChoice,
    val options: List<AnswerOption>? = null,
    /** Optional questions can be skipped with an empty answer. */
    val isOptional: Boolean = false,
    /** When set, the question is asked only if the condition holds and is skipped otherwise. */
    val showIf: QuestionCondition? = null,
)

enum class SectionDisplayMode { Sequential, AllAtOnce }

data class TestSection(
    val id: Int,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val questions: List<Question>,
    val displayMode: SectionDisplayMode = SectionDisplayMode.Sequential,
)

sealed interface AnswerValue {
    data class Option(val option: AnswerOption) : AnswerValue
    data class Scale(val value: Int) : AnswerValue
    data class Text(val text: String) : AnswerValue
}

data class UserAnswer(val sectionId: Int, val questionId: Int, val answerValue: AnswerValue)

object TestData {

    private fun options(vararg entries: Pair<Int, Int>): List<AnswerOption> =
        entries.mapIndexed { index, (textRes, value) -> AnswerOption(id = index + 1, textRes = textRes, value = value) }

    private val phqOptions = options(
        R.string.section_b_opt1 to 0,
        R.string.section_b_opt2 to 1,
        R.string.section_b_opt3 to 2,
        R.string.section_b_opt4 to 3,
    )

    private val sectionCOptions = options(
        R.string.section_c_opt1 to 0,
        R.string.section_c_opt2 to 1,
        R.string.section_c_opt3 to 2,
        R.string.section_c_opt4 to 3,
    )

    /** Rosenberg answers run from "strongly agree" (3) to "strongly disagree" (0). */
    private val rsesOptions = options(
        R.string.section_d_opt1 to 3,
        R.string.section_d_opt2 to 2,
        R.string.section_d_opt3 to 1,
        R.string.section_d_opt4 to 0,
    )

    private val sectionBQuestions = listOf(
        R.string.qb_1, R.string.qb_2, R.string.qb_3, R.string.qb_4, R.string.qb_5,
        R.string.qb_6, R.string.qb_7, R.string.qb_8, R.string.qb_9, R.string.qb_10,
    )
    private val sectionCQuestions = listOf(
        R.string.qc_1, R.string.qc_2, R.string.qc_3, R.string.qc_4, R.string.qc_5, R.string.qc_6, R.string.qc_7,
    )
    private val sectionDQuestions = listOf(
        R.string.qd_1, R.string.qd_2, R.string.qd_3, R.string.qd_4, R.string.qd_5,
        R.string.qd_6, R.string.qd_7, R.string.qd_8, R.string.qd_9, R.string.qd_10,
    )

    val sections: List<TestSection> = listOf(
        // Section A: demographics. Only age and gender are asked.
        TestSection(
            id = 1,
            titleRes = R.string.section_a_title,
            descriptionRes = R.string.section_a_description,
            questions = listOf(
                Question(id = 1, textRes = R.string.qa_1, type = QuestionType.IntegerInput(PredictionRequestBuilder.AGE_RANGE)),
                Question(
                    id = 2,
                    textRes = R.string.qa_2,
                    options = options(R.string.qa_2_opt1 to 1, R.string.qa_2_opt2 to 2, R.string.qa_2_opt3 to 3),
                ),
            ),
        ),
        // Section B: PHQ-9 (ids 7-15) and the attention check (id 16).
        TestSection(
            id = 2,
            titleRes = R.string.section_b_title,
            descriptionRes = R.string.section_b_description,
            questions = sectionBQuestions.mapIndexed { index, res -> Question(id = 7 + index, textRes = res, options = phqOptions) },
        ),
        // Section C (ids 17-23).
        TestSection(
            id = 3,
            titleRes = R.string.section_c_title,
            descriptionRes = R.string.section_c_description,
            questions = sectionCQuestions.mapIndexed { index, res -> Question(id = 17 + index, textRes = res, options = sectionCOptions) },
        ),
        // Section D: Rosenberg self-esteem scale (ids 24-33).
        TestSection(
            id = 4,
            titleRes = R.string.section_d_title,
            descriptionRes = R.string.section_d_description,
            questions = sectionDQuestions.mapIndexed { index, res -> Question(id = 24 + index, textRes = res, options = rsesOptions) },
        ),
    )

    fun question(id: Int): Question? = sections.asSequence().flatMap { it.questions }.firstOrNull { it.id == id }
}
