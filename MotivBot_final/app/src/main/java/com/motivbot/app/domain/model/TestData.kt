package com.motivbot.app.domain.model

import com.motivbot.app.R

/**
 * Port of `TestData` in `Models.swift` — the seven psychometric sections, unchanged in
 * ordering, ids, option values and display modes.
 *
 * Kept as a `val` on an `object` (Swift's `static let`). It is pure, immutable data, so it needs
 * no injection; the repository/ViewModel read it directly the way the Swift ViewModel did.
 */
object TestData {

    /**
     * Accepted range for the age question.
     *
     * The lower bound is a policy decision, not a technical one: PHQ-9 and GAD-7 are not validated
     * for young children, and collecting mental-health data from under-13s pulls in guardian-consent
     * obligations (GDPR Art. 8, COPPA). 13 is a permissive floor that still rejects obvious
     * nonsense — raise it to 18 if the study protocol requires adults only.
     */
    val AGE_RANGE: IntRange = 13..120

    /** Option sets that repeat across every question of a section, hoisted out of the loops. */
    private fun likert(vararg pairs: Pair<Int, Int>): List<AnswerOption> =
        pairs.mapIndexed { index, (textRes, value) ->
            AnswerOption(id = index + 1, textRes = textRes, value = value)
        }

    private val sectionBOptions = likert(
        R.string.section_b_opt1 to 0,
        R.string.section_b_opt2 to 1,
        R.string.section_b_opt3 to 2,
        R.string.section_b_opt4 to 3,
    )

    private val sectionCOptions = likert(
        R.string.section_c_opt1 to 0,
        R.string.section_c_opt2 to 1,
        R.string.section_c_opt3 to 2,
        R.string.section_c_opt4 to 3,
    )

    private val sectionDOptions = likert(
        R.string.section_d_opt1 to 3,
        R.string.section_d_opt2 to 2,
        R.string.section_d_opt3 to 1,
        R.string.section_d_opt4 to 0,
    )

    private val sectionEOptions = likert(
        R.string.section_e_opt1 to 1,
        R.string.section_e_opt2 to 2,
        R.string.section_e_opt3 to 3,
        R.string.section_e_opt4 to 4,
        R.string.section_e_opt5 to 5,
    )

    private val sectionGOptions = likert(
        R.string.section_g_opt1 to 1,
        R.string.section_g_opt2 to 2,
        R.string.section_g_opt3 to 3,
        R.string.section_g_opt4 to 4,
        R.string.section_g_opt5 to 5,
        R.string.section_g_opt6 to 6,
        R.string.section_g_opt7 to 7,
    )

    private val sectionBQuestionText = listOf(
        R.string.qb_1, R.string.qb_2, R.string.qb_3, R.string.qb_4, R.string.qb_5,
        R.string.qb_6, R.string.qb_7, R.string.qb_8, R.string.qb_9, R.string.qb_10,
    )

    private val sectionCQuestionText = listOf(
        R.string.qc_1, R.string.qc_2, R.string.qc_3, R.string.qc_4,
        R.string.qc_5, R.string.qc_6, R.string.qc_7,
    )

    private val sectionDQuestionText = listOf(
        R.string.qd_1, R.string.qd_2, R.string.qd_3, R.string.qd_4, R.string.qd_5,
        R.string.qd_6, R.string.qd_7, R.string.qd_8, R.string.qd_9, R.string.qd_10,
    )

    private val sectionEQuestionText = listOf(
        R.string.qe_1, R.string.qe_2, R.string.qe_3, R.string.qe_4, R.string.qe_5,
        R.string.qe_6, R.string.qe_7, R.string.qe_8, R.string.qe_9, R.string.qe_10,
        R.string.qe_11, R.string.qe_12, R.string.qe_13, R.string.qe_14, R.string.qe_15,
        R.string.qe_16, R.string.qe_17, R.string.qe_18, R.string.qe_19, R.string.qe_20,
    )

    private val sectionGQuestionText = listOf(
        R.string.qg_1, R.string.qg_2, R.string.qg_3, R.string.qg_4,
        R.string.qg_5, R.string.qg_6, R.string.qg_7,
    )

    /**
     * Builds the questions of a uniform section. `idOffset` reproduces the Swift id arithmetic
     * (`6 + i`, `16 + i`, …) so question ids stay identical across the two apps — important
     * because ids are what the CSV export and the AI prompt are keyed on.
     */
    private fun uniformSection(
        textResources: List<Int>,
        idOffset: Int,
        options: List<AnswerOption>,
    ): List<Question> = textResources.mapIndexed { index, textRes ->
        Question(
            id = idOffset + index + 1,
            textRes = textRes,
            type = QuestionType.SingleChoice,
            options = options,
        )
    }

    val sections: List<TestSection> = listOf(
        // ── SECTION A — Demographics (mixed question types) ─────────────────────────────
        TestSection(
            id = 1,
            titleRes = R.string.section_a_title,
            descriptionRes = R.string.section_a_description,
            questions = listOf(
                Question(id = 1, textRes = R.string.qa_1, type = QuestionType.Number(AGE_RANGE)),
                Question(
                    id = 2,
                    textRes = R.string.qa_2,
                    options = likert(
                        R.string.qa_2_opt1 to 1,
                        R.string.qa_2_opt2 to 2,
                        R.string.qa_2_opt3 to 3,
                    ),
                ),
                Question(
                    id = 3,
                    textRes = R.string.qa_3,
                    options = likert(
                        R.string.qa_3_opt1 to 1,
                        R.string.qa_3_opt2 to 2,
                        R.string.qa_3_opt3 to 3,
                        R.string.qa_3_opt4 to 4,
                        R.string.qa_3_opt5 to 5,
                    ),
                ),
                Question(
                    id = 4,
                    textRes = R.string.qa_4,
                    options = likert(
                        R.string.qa_4_opt1 to 1,
                        R.string.qa_4_opt2 to 2,
                        R.string.qa_4_opt3 to 3,
                        R.string.qa_4_opt4 to 4,
                    ),
                ),
                Question(
                    id = 5,
                    textRes = R.string.qa_5,
                    options = likert(
                        R.string.qa_5_opt1 to 1,
                        R.string.qa_5_opt2 to 0,
                    ),
                ),
                Question(id = 6, textRes = R.string.qa_6, type = QuestionType.FreeText),
            ),
        ),

        // ── SECTION B — Patient Health Questionnaire (PHQ-9 + attention check) ──────────
        TestSection(
            id = 2,
            titleRes = R.string.section_b_title,
            descriptionRes = R.string.section_b_description,
            questions = uniformSection(sectionBQuestionText, idOffset = 6, options = sectionBOptions),
        ),

        // ── SECTION C — Generalized Anxiety Disorder scale (GAD-7) ─────────────────────
        TestSection(
            id = 3,
            titleRes = R.string.section_c_title,
            descriptionRes = R.string.section_c_description,
            questions = uniformSection(sectionCQuestionText, idOffset = 16, options = sectionCOptions),
        ),

        // ── SECTION D — Rosenberg Self-Esteem Scale ────────────────────────────────────
        TestSection(
            id = 4,
            titleRes = R.string.section_d_title,
            descriptionRes = R.string.section_d_description,
            questions = uniformSection(sectionDQuestionText, idOffset = 23, options = sectionDOptions),
        ),

        // ── SECTION E — PANAS, rendered as a single grid page ──────────────────────────
        TestSection(
            id = 5,
            titleRes = R.string.section_e_title,
            descriptionRes = R.string.section_e_description,
            questions = uniformSection(sectionEQuestionText, idOffset = 33, options = sectionEOptions),
            displayMode = SectionDisplayMode.AllAtOnce,
        ),

        // ── SECTION F — Self-Assessment Manikin (1–9 sliders with SAM artwork) ─────────
        TestSection(
            id = 6,
            titleRes = R.string.section_f_title,
            descriptionRes = R.string.section_f_description,
            questions = listOf(
                Question(
                    id = 54,
                    textRes = R.string.qf_1,
                    type = QuestionType.Scale(1..9),
                    imageRes = R.drawable.image1,
                ),
                Question(
                    id = 55,
                    textRes = R.string.qf_2,
                    type = QuestionType.Scale(1..9),
                    imageRes = R.drawable.image2,
                ),
            ),
        ),

        // ── SECTION G — State Self-Esteem Scale ────────────────────────────────────────
        TestSection(
            id = 7,
            titleRes = R.string.section_g_title,
            descriptionRes = R.string.section_g_description,
            questions = uniformSection(sectionGQuestionText, idOffset = 55, options = sectionGOptions),
        ),
    )

    /** Swift: `viewModel.totalQuestions`. Constant for the lifetime of the process. */
    val totalQuestions: Int = sections.sumOf { it.questions.size }
}
