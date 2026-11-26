package com.motivbot.app

import com.motivbot.app.data.remote.GradioResultReader
import com.motivbot.app.data.remote.MotivBotApiService
import com.motivbot.app.domain.Level
import com.motivbot.app.domain.MessageCategoryStyle
import com.motivbot.app.domain.MotivBotApiError
import com.motivbot.app.domain.MotivBotApiException
import com.motivbot.app.domain.PipelineLanguage
import com.motivbot.app.domain.PredictionRequest
import com.motivbot.app.domain.PredictionRequestBuilder
import com.motivbot.app.domain.PredictionResponse
import com.motivbot.app.domain.WellBeingDimension
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

// Port of PredictionTests.swift (request builder, response decoding) and the validation tests in IntegrationTests.swift.

class PredictionRequestBuilderTest {

    @Test
    fun encodesNamedPipelineParameters() {
        val request = PredictionRequestBuilder.build(
            emotionText = "  Herkes beni sinirlendiriyor, artık tahammülüm kalmadı \n",
            answers = Fixture.answers(),
        )
        val json = Json.parseToJsonElement(GradioResultReader.json.encodeToString(PredictionRequest.WireBody.serializer(), request.toWire()))
        // PHQ-9 = 10; RSES = 24 on the 0-30 scale, sent as 34 on the pipeline's 10-40 scale.
        val expected = Json.parseToJsonElement(
            """{"text": "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı", "age": 25, "gender": "Erkek", "phq9": 10, "rses": 34}""",
        )
        assertEquals(expected, json)
    }

    @Test
    fun excludesAttentionCheck() {
        val request = PredictionRequestBuilder.build("text", Fixture.answers(phq9 = List(9) { 0 }, attentionCheck = 3))
        assertEquals(List(9) { 0 }, request.phq9Items)
        assertEquals(0, request.phq9Score)
    }

    @Test
    fun phq9ScoreIsSumOfItems() {
        assertEquals(27, PredictionRequestBuilder.build("text", Fixture.answers(phq9 = List(9) { 3 })).phq9Score)
    }

    @Test
    fun rsesScoreAppliesReverseItems() {
        val cases = listOf(
            // Agreeing with every positive statement and disagreeing with every negative one is the maximum.
            listOf(3, 3, 0, 3, 0, 3, 3, 0, 0, 0) to 30,
            listOf(0, 0, 3, 0, 3, 0, 0, 3, 3, 3) to 0,
            List(10) { 3 } to 15,
            listOf(3, 2, 1, 2, 0, 3, 2, 1, 1, 0) to 24,
        )
        for ((items, expected) in cases) {
            val request = PredictionRequestBuilder.build("text", Fixture.answers(rses = items))
            assertEquals(items, request.rsesItems)
            assertEquals(expected, request.rsesScore)
            assertEquals(expected + 10, request.pipelineRsesScore)
        }
    }

    @Test
    fun mapsGender() {
        val cases = listOf(
            Triple(1, PredictionRequest.Gender.Male, "Erkek" to 0.0),
            Triple(2, PredictionRequest.Gender.Female, "Kadın" to 1.0),
            Triple(3, PredictionRequest.Gender.Unspecified, "Non-binary" to 0.5),
        )
        for ((optionValue, expected, wire) in cases) {
            val request = PredictionRequestBuilder.build("text", Fixture.answers(genderValue = optionValue))
            assertEquals(expected, request.gender)
            assertEquals(wire.first, request.gender.rawValue)
            assertEquals(wire.second, request.gender.numericValue, 0.0)
        }
    }

    @Test
    fun rejectsMissingAnswers() {
        val answers = Fixture.answers().filter { it.questionId != 30 }
        val error = assertThrows(PredictionRequestBuilder.BuildError::class.java) { PredictionRequestBuilder.build("text", answers) }
        assertEquals(PredictionRequestBuilder.BuildError.MissingAnswer(30), error)
    }

    @Test
    fun rejectsEmptyText() {
        val error = assertThrows(PredictionRequestBuilder.BuildError::class.java) {
            PredictionRequestBuilder.build("  \n", Fixture.answers())
        }
        assertEquals(PredictionRequestBuilder.BuildError.EmptyText, error)
    }

    @Test
    fun parsesValidAge() {
        val cases = listOf("25" to 25, " 42 " to 42, "18" to 18, "100" to 100, "۲۵" to 25, "٣٠" to 30)
        for ((input, expected) in cases) assertEquals(input, expected, PredictionRequestBuilder.parseAge(input))
    }

    @Test
    fun rejectsInvalidAge() {
        for (input in listOf("", "abc", "9", "17", "101", "2.5", "-20", "25a", "1000")) {
            assertNull(input, PredictionRequestBuilder.parseAge(input))
        }
    }
}

class PredictionResponseTest {

    @Test
    fun decodesStructuredJson() {
        val response = Fixture.response()
        assertEquals(0.88, response.probability(WellBeingDimension.Anxiety)!!, 0.0)
        assertEquals(Level.High, response.level(WellBeingDimension.Anxiety))
        assertEquals(Level.No, response.level(WellBeingDimension.SelfEsteem))
        assertEquals(3, response.messages.size)
        assertEquals("Emotional Validation", response.messages[0].category)
        assertEquals("Hiçbir şey sonsuza kadar sürmez.", response.messages[0].text)
        assertEquals("Matt Haig", response.messages[0].author)
        assertNull(response.messages[1].author)
    }

    @Test
    fun levelThresholds() {
        val cases = listOf(0.0 to Level.No, 0.24 to Level.No, 0.25 to Level.Low, 0.49 to Level.Low, 0.5 to Level.High, 1.0 to Level.High)
        for ((probability, expected) in cases) {
            val response = PredictionResponse(mapOf("Anxiety_Fear_Worry" to probability), emptyList())
            assertEquals("$probability", expected, response.level(WellBeingDimension.Anxiety))
            assertNull(response.level(WellBeingDimension.Depression))
        }
    }

    @Test
    fun parsesCompleteEventWithObjectPayload() {
        val stream = "event: complete\ndata: [\"### Summary\", ${Fixture.structuredJson}]\n\n"
        assertEquals(Fixture.response(), MotivBotApiService.parseEventStream(stream))
    }

    @Test
    fun parsesCompleteEventWithEscapedStringPayload() {
        val escaped = JsonPrimitive(Fixture.structuredJson).toString()
        val stream = "event: generating\ndata: null\n\nevent: complete\ndata: [\"### Summary\", $escaped]\n\n"
        assertEquals(Fixture.response(), MotivBotApiService.parseEventStream(stream))
    }

    @Test
    fun errorEventBecomesInvalidInput() {
        val stream = "event: error\ndata: {\"error\": \"Value 15 is less than minimum value 18.\"}\n\n"
        val error = assertThrows(MotivBotApiException::class.java) { MotivBotApiService.parseEventStream(stream) }
        assertEquals(MotivBotApiError.InvalidInput("Value 15 is less than minimum value 18."), error.error)
    }

    @Test
    fun positiveAffectToneIsInverted() {
        assertEquals(WellBeingDimension.Tone.Steady, WellBeingDimension.PositiveAffect.tone(Level.High))
        assertEquals(WellBeingDimension.Tone.Care, WellBeingDimension.PositiveAffect.tone(Level.No))
        assertEquals(WellBeingDimension.Tone.Care, WellBeingDimension.Anxiety.tone(Level.High))
        assertEquals(WellBeingDimension.Tone.Steady, WellBeingDimension.Anxiety.tone(Level.No))
    }

    @Test
    fun deckOrdersMessagesByCategoryThenPipelineOrder() {
        fun message(category: String, text: String) = PredictionResponse.Message(text = text, category = category, score = 1.0)
        val messages = listOf(
            message("Action-Oriented Encouragement", "a1"),
            message("Something new", "o1"),
            message("Emotional Validation", "v1"),
            message("Cognitive Reframing", "r1"),
            message("Emotional Validation", "v2"),
        )
        assertEquals(listOf("v1", "v2", "r1", "a1", "o1"), MessageCategoryStyle.ordered(messages).map { it.text })
    }
}

class ValidationTest {

    @Test
    fun acceptsValidRequest() {
        Fixture.validRequest().validate()
    }

    @Test
    fun rejectsInvalidRequest() {
        data class Case(val text: String, val age: Int, val phq9: List<Int>, val rses: List<Int>)
        val text = "yeterince uzun bir metin"
        val cases = listOf(
            Case("123456789", 25, Fixture.phq9, Fixture.rses),
            Case("a".repeat(2001), 25, Fixture.phq9, Fixture.rses),
            Case(text, 17, Fixture.phq9, Fixture.rses),
            Case(text, 101, Fixture.phq9, Fixture.rses),
            Case(text, 25, Fixture.phq9.take(8), Fixture.rses),
            Case(text, 25, Fixture.phq9, Fixture.rses.map { it + 1 }),
        )
        for (case in cases) {
            val request = PredictionRequest(case.text, case.age, PredictionRequest.Gender.Male, case.phq9, case.rses)
            assertThrows(case.toString(), MotivBotApiException::class.java) { request.validate() }
        }
    }

    @Test
    fun detectsLanguageLikeThePipeline() {
        val cases = listOf(
            "Bugün kendimi çok yorgun hissediyorum." to PipelineLanguage.Turkish,
            "ben bugun cok yorgunum ve mutsuzum" to PipelineLanguage.Turkish,
            "Bugun kendimi kotu hissediyorum" to PipelineLanguage.English,
            "I feel very tired and anxious today." to PipelineLanguage.English,
            "امروز خیلی خسته و نگران هستم" to PipelineLanguage.Persian,
            "I feel سلام today and every day" to PipelineLanguage.English,
        )
        for ((text, expected) in cases) assertEquals(text, expected, PipelineLanguage.detect(text))
    }
}
