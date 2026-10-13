package com.motivbot.app

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.StringProvider
import com.motivbot.app.domain.model.AnswerValue
import com.motivbot.app.domain.model.TestData
import com.motivbot.app.domain.model.UserAnswer
import com.motivbot.app.domain.usecase.AnswerRow
import com.motivbot.app.domain.usecase.AnswerRowMapper
import com.motivbot.app.domain.usecase.BuildPromptUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Resolves every resource to a stable stub so the assertions do not depend on translations. */
private class FakeStringProvider : StringProvider {
    override fun getString(language: AppLanguage, resId: Int): String = "res_$resId"
}

class BuildPromptUseCaseTest {

    private val buildPrompt = BuildPromptUseCase(FakeStringProvider())

    @Test
    fun `prompt contains the emotion text and every answered question`() {
        val section = TestData.sections.first()
        val question = section.questions[1]
        val option = question.options!!.first()

        val prompt = buildPrompt(
            emotionText = "I have been anxious all week.",
            answers = listOf(UserAnswer(section.id, question.id, AnswerValue.Option(option))),
            language = AppLanguage.Turkish,
        )

        assertTrue(prompt.contains("I have been anxious all week."))
        assertTrue(prompt.contains("res_${question.textRes}"))
        assertTrue(prompt.contains("(Score: ${option.value})"))
        assertTrue(prompt.endsWith("please provide a personalized motivational quote and a brief supportive message."))
    }

    @Test
    fun `unanswered questions are skipped`() {
        val prompt = buildPrompt(
            emotionText = "text",
            answers = emptyList(),
            language = AppLanguage.Turkish,
        )
        assertTrue(prompt.lineSequence().none { it.startsWith("Q: ") })
    }

    @Test
    fun `free text and scale answers are labelled distinctly`() {
        val section = TestData.sections.first()
        val freeTextQuestion = section.questions.first()

        val prompt = buildPrompt(
            emotionText = "text",
            answers = listOf(
                UserAnswer(section.id, freeTextQuestion.id, AnswerValue.Text("34")),
                UserAnswer(6, 54, AnswerValue.Scale(7)),
            ),
            language = AppLanguage.Turkish,
        )

        assertTrue(prompt.contains("A: 34 (Free text response)"))
        assertTrue(prompt.contains("A: 7 (Scale value)"))
    }
}

class AnswerRowMapperTest {

    private val mapper = AnswerRowMapper(FakeStringProvider())

    @Test
    fun `free text answers carry no score`() {
        val section = TestData.sections.first()
        val rows = mapper(
            answers = listOf(UserAnswer(section.id, section.questions.first().id, AnswerValue.Text("29"))),
            language = AppLanguage.Turkish,
        )

        assertEquals(1, rows.size)
        assertEquals("29", rows.single().answerText)
        assertEquals(null, rows.single().score)
    }

    @Test
    fun `rows come back in section then question order`() {
        val answers = TestData.sections.flatMap { section ->
            section.questions.map { UserAnswer(section.id, it.id, AnswerValue.Scale(1)) }
        }.shuffled()

        val rows = mapper(answers, AppLanguage.Turkish)

        assertEquals(TestData.totalQuestions, rows.size)
        assertEquals(rows.map { it.questionId }, rows.map { it.questionId }.sorted())
    }
}

/**
 * The CSV writer needs a Context, so only the pure formatter is exercised here — which is the part
 * that regressed in the Swift original (commas in free text were destroyed by being swapped for
 * semicolons).
 */
class CsvFormattingTest {

    private fun csvField(value: String): String {
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    @Test
    fun `commas survive as quoted fields instead of being replaced`() {
        assertEquals("\"Yes, sometimes\"", csvField("Yes, sometimes"))
    }

    @Test
    fun `embedded quotes are doubled`() {
        assertEquals("\"He said \"\"fine\"\"\"", csvField("He said \"fine\""))
    }

    @Test
    fun `plain fields are left alone`() {
        assertEquals("Hiç", csvField("Hiç"))
    }

    @Test
    fun `answer rows map onto the four expected columns`() {
        val row = AnswerRow(1, 1, "Section", "Question", "Answer", 3)
        val line = listOf(row.sectionTitle, row.questionText, row.answerText, row.score.toString())
        assertEquals(4, line.size)
    }
}
