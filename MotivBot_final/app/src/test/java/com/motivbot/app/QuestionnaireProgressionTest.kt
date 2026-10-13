package com.motivbot.app

import com.motivbot.app.domain.model.AnswerValue
import com.motivbot.app.domain.model.QuestionType
import com.motivbot.app.domain.model.SectionDisplayMode
import com.motivbot.app.domain.model.TestData
import com.motivbot.app.domain.model.UserAnswer
import com.motivbot.app.ui.WellBeingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the parts of the port that are easy to get subtly wrong: the section/question id
 * arithmetic copied from Swift, and the `canProceed` gating rules.
 */
class QuestionnaireProgressionTest {

    @Test
    fun `test data matches the Swift structure`() {
        assertEquals(7, TestData.sections.size)
        assertEquals(listOf(6, 10, 7, 10, 20, 2, 7), TestData.sections.map { it.questions.size })
        assertEquals(62, TestData.totalQuestions)
    }

    @Test
    fun `question ids are unique and contiguous from 1`() {
        val ids = TestData.sections.flatMap { section -> section.questions.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals((1..62).toList(), ids.sorted())
    }

    @Test
    fun `only section E is rendered all at once`() {
        val gridSections = TestData.sections
            .filter { it.displayMode == SectionDisplayMode.AllAtOnce }
            .map { it.id }
        assertEquals(listOf(5), gridSections)
    }

    @Test
    fun `section F questions are 1 to 9 scales with artwork`() {
        val sectionF = TestData.sections.first { it.id == 6 }
        sectionF.questions.forEach { question ->
            assertEquals(QuestionType.Scale(1..9), question.type)
            assertTrue("expected artwork on ${question.id}", question.imageRes != null)
        }
    }

    @Test
    fun `emotion input requires at least ten characters`() {
        assertFalse(WellBeingUiState(emotionText = "   sad   ").canProceedEmotion)
        assertTrue(WellBeingUiState(emotionText = "I feel quite low today").canProceedEmotion)
    }

    @Test
    fun `single choice question blocks progress until an option is picked`() {
        val state = WellBeingUiState(currentSectionIndex = 1, currentQuestionIndex = 0)
        assertFalse(state.canProceedQuestion)
        assertTrue(state.copy(selectedOptionId = 1).canProceedQuestion)
    }

    @Test
    fun `scale question can always proceed`() {
        // Section F (index 5) is the scale section.
        assertTrue(WellBeingUiState(currentSectionIndex = 5, currentQuestionIndex = 0).canProceedQuestion)
    }

    @Test
    fun `grid section requires every row answered`() {
        val sectionEIndex = TestData.sections.indexOfFirst { it.id == 5 }
        val sectionE = TestData.sections[sectionEIndex]

        val partial = WellBeingUiState(
            currentSectionIndex = sectionEIndex,
            gridAnswers = sectionE.questions.dropLast(1).associate { it.id to 1 },
        )
        assertFalse(partial.canProceedQuestion)

        val complete = partial.copy(gridAnswers = sectionE.questions.associate { it.id to 1 })
        assertTrue(complete.canProceedQuestion)
    }

    @Test
    fun `isLastQuestion is only true on the final question of the final section`() {
        val lastSectionIndex = TestData.sections.lastIndex
        val lastQuestionIndex = TestData.sections.last().questions.lastIndex

        assertTrue(
            WellBeingUiState(
                currentSectionIndex = lastSectionIndex,
                currentQuestionIndex = lastQuestionIndex,
            ).isLastQuestion,
        )
        assertFalse(
            WellBeingUiState(
                currentSectionIndex = lastSectionIndex,
                currentQuestionIndex = lastQuestionIndex - 1,
            ).isLastQuestion,
        )
    }

    @Test
    fun `progress is answered over total`() {
        val answers = List(31) { index ->
            UserAnswer(sectionId = 1, questionId = index + 1, answerValue = AnswerValue.Scale(3))
        }
        assertEquals(0.5f, WellBeingUiState(answers = answers).overallProgress, 0.001f)
    }
}
