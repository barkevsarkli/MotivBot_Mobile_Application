package com.motivbot.app

import com.motivbot.app.domain.model.QuestionType
import com.motivbot.app.domain.model.TestData
import com.motivbot.app.ui.WellBeingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The age question used to be free text, so every string below was an accepted "age" that reached
 * the AI prompt and the research CSV. These tests pin the replacement shut.
 */
class AgeInputTest {

    /** Section A / question 1 is the age question; index 0,0 in the questionnaire. */
    private fun state(input: String) = WellBeingUiState(
        currentSectionIndex = 0,
        currentQuestionIndex = 0,
        numberAnswer = input,
    )

    /** Mirrors `WellBeingViewModel.onNumberChange`, which is what the UI can actually put in state. */
    private fun filtered(raw: String) = raw.filter(Char::isDigit).take(3)

    @Test
    fun `the age question is a bounded number, not free text`() {
        val ageQuestion = TestData.sections.first().questions.first()
        assertEquals(QuestionType.Number(TestData.AGE_RANGE), ageQuestion.type)
    }

    @Test
    fun `plausible ages are accepted`() {
        listOf("13", "28", "45", "99", "120").forEach { age ->
            assertTrue("expected $age to be accepted", state(age).canProceedQuestion)
            assertEquals(age.toInt(), state(age).numberAnswerValue)
        }
    }

    @Test
    fun `out-of-range numbers are rejected`() {
        listOf("0", "5", "12", "121", "999").forEach { age ->
            assertFalse("expected $age to be rejected", state(age).canProceedQuestion)
            assertNull(state(age).numberAnswerValue)
            assertTrue("expected an inline error for $age", state(age).showNumberError)
        }
    }

    @Test
    fun `an empty field blocks progress without showing an error`() {
        val empty = state("")
        assertFalse(empty.canProceedQuestion)
        assertNull(empty.numberAnswerValue)
        // Nothing typed yet — nagging before the user has entered anything would be hostile.
        assertFalse(empty.showNumberError)
    }

    @Test
    fun `garbage that free text used to accept can no longer reach state`() {
        // Each of these was a valid "age" when the question was FreeText.
        val garbage = mapOf(
            "asdasd" to "",
            "yirmi sekiz" to "",
            "🎂🎂" to "",
            "-5" to "5",          // the sign is stripped; 5 then fails the range check
            "28.7" to "287",      // the dot is stripped; 287 fails the range check
            "  " to "",
            "twenty" to "",
            "<script>alert(1)</script>" to "1",
            "999999999999999999999" to "999", // capped, so toIntOrNull() cannot overflow
        )

        garbage.forEach { (raw, expectedState) ->
            val inState = filtered(raw)
            assertEquals("filtering of $raw", expectedState, inState)
            assertFalse("expected $raw to be rejected", state(inState).canProceedQuestion)
        }
    }

    @Test
    fun `a very long digit paste is capped rather than overflowing`() {
        val huge = "9".repeat(10_000)
        val inState = filtered(huge)

        // The old free-text path would have shipped all 10 000 characters to OpenAI and into the
        // CSV. Capping at three digits also keeps toIntOrNull() well inside Int range.
        assertEquals("999", inState)
        assertEquals(3, inState.length)
        assertFalse(state(inState).canProceedQuestion) // 999 is out of range
    }

    @Test
    fun `ignoring the range still cannot record an answer`() {
        // canProceedQuestion is the only gate nextQuestion() honours, so if it says no,
        // no UserAnswer can be produced for this question.
        assertFalse(state("7").canProceedQuestion)
        assertFalse(state("500").canProceedQuestion)
    }
}
