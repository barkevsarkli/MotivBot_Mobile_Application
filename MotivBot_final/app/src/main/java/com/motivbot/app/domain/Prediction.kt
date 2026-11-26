package com.motivbot.app.domain

import com.motivbot.app.core.AppLanguage
import kotlinx.serialization.Serializable
import java.text.BreakIterator

// Port of the API contract in MotivBotAPIService.swift and of PredictionRequestBuilder.swift.
//
// The MotivBot unified pipeline on Hugging Face Spaces (Gradio), named-parameter API:
//   1. POST {base}/gradio_api/call/v2/run_pipeline
//      body {"text": ..., "age": ..., "gender": ..., "phq9": ..., "rses": ...} -> {"event_id": "..."}
//   2. GET  {base}/gradio_api/call/run_pipeline/{event_id}
//      -> server-sent events; "event: complete" carries data = [markdown summary, structured JSON]
// The scores are computed in the app. The pipeline expects RSES on 10-40, so the 0-30 total is sent with +10.

data class PredictionRequest(
    val text: String,
    val age: Int,
    val gender: Gender,
    val phq9Items: List<Int>,
    val rsesItems: List<Int>,
) {
    enum class Gender(val rawValue: String, val numericValue: Double) {
        Male("Erkek", 0.0),
        Female("Kadın", 1.0),
        Unspecified("Non-binary", 0.5),
    }

    val phq9Score: Int get() = phq9Items.sum()

    /** Rosenberg total on 0-30. Items 3, 5, 8, 9 and 10 are reverse scored. */
    val rsesScore: Int
        get() = rsesItems.withIndex().sumOf { (index, value) ->
            if ((index + 1) in RSES_REVERSE_ITEMS) 3 - value else value
        }

    val pipelineRsesScore: Int get() = rsesScore + PIPELINE_RSES_OFFSET

    /** PHQ-9 item 9 (thoughts of self-harm). */
    val selfHarmItem: Int get() = if (phq9Items.size == 9) phq9Items[8] else 0

    /** Throws [MotivBotApiException] with [MotivBotApiError.InvalidInput] when the pipeline would reject it. */
    fun validate() {
        val length = text.trim().graphemeCount()
        if (length !in TEXT_LENGTH_RANGE) throw MotivBotApiException(MotivBotApiError.InvalidInput("text length"))
        if (age !in PredictionRequestBuilder.AGE_RANGE) throw MotivBotApiException(MotivBotApiError.InvalidInput("age"))
        if (phq9Items.size != 9 || phq9Items.any { it !in ITEM_RANGE }) {
            throw MotivBotApiException(MotivBotApiError.InvalidInput("phq9 items"))
        }
        if (rsesItems.size != 10 || rsesItems.any { it !in ITEM_RANGE }) {
            throw MotivBotApiException(MotivBotApiError.InvalidInput("rses items"))
        }
    }

    /** The JSON body of the submit call. */
    fun toWire(): WireBody = WireBody(text = text, age = age, gender = gender.rawValue, phq9 = phq9Score, rses = pipelineRsesScore)

    @Serializable
    data class WireBody(val text: String, val age: Int, val gender: String, val phq9: Int, val rses: Int)

    companion object {
        const val PIPELINE_RSES_OFFSET = 10
        val RSES_REVERSE_ITEMS = setOf(3, 5, 8, 9, 10)
        val TEXT_LENGTH_RANGE = 10..2000
        val ITEM_RANGE = 0..3
    }
}

/** Maps the questionnaire answers to a [PredictionRequest]. Pure: no state, no networking. */
object PredictionRequestBuilder {

    sealed class BuildError : Exception() {
        data object EmptyText : BuildError()
        data class MissingAnswer(val questionId: Int) : BuildError()
        data object InvalidAge : BuildError()
    }

    const val AGE_QUESTION_ID = 1
    const val GENDER_QUESTION_ID = 2

    /** qb_1 ... qb_9. qb_10 (id 16) is an attention check and is never sent. */
    val PHQ9_QUESTION_IDS = (7..15).toList()
    const val ATTENTION_CHECK_QUESTION_ID = 16

    /** qd_1 ... qd_10, kept as raw option values. [PredictionRequest.rsesScore] applies the reverse scoring. */
    val RSES_QUESTION_IDS = (24..33).toList()

    /** The pipeline rejects ages outside 18-100. */
    val AGE_RANGE = 18..100

    fun build(emotionText: String, answers: List<UserAnswer>): PredictionRequest {
        val text = emotionText.trim()
        if (text.isEmpty()) throw BuildError.EmptyText
        val ageText = (answer(AGE_QUESTION_ID, answers) as? AnswerValue.Text)?.text
            ?: throw BuildError.MissingAnswer(AGE_QUESTION_ID)
        val age = parseAge(ageText) ?: throw BuildError.InvalidAge
        return PredictionRequest(
            text = text,
            age = age,
            gender = gender(answers),
            phq9Items = optionValues(PHQ9_QUESTION_IDS, answers),
            rsesItems = optionValues(RSES_QUESTION_IDS, answers),
        )
    }

    /**
     * Parses an age typed by the user. Accepts any Unicode decimal digits (e.g. Persian "۲۵").
     * Returns null unless the value is a whole number in [AGE_RANGE].
     */
    fun parseAge(text: String): Int? {
        val value = parseWholeNumber(text.trim(), maxDigits = 3) ?: return null
        return value.takeIf { it in AGE_RANGE }
    }

    /** The value of a string made only of decimal digits (any script), or null. */
    fun parseWholeNumber(text: String, maxDigits: Int = Int.MAX_VALUE): Int? {
        val codePoints = text.codePoints().toArray()
        if (codePoints.isEmpty() || codePoints.size > maxDigits) return null
        var value = 0
        for (codePoint in codePoints) {
            if (!Character.isDigit(codePoint)) return null
            val digit = Character.digit(codePoint, 10)
            if (digit < 0) return null
            value = value * 10 + digit
            if (value > 100_000) return null
        }
        return value
    }

    private fun gender(answers: List<UserAnswer>): PredictionRequest.Gender {
        val option = (answer(GENDER_QUESTION_ID, answers) as? AnswerValue.Option)?.option
            ?: throw BuildError.MissingAnswer(GENDER_QUESTION_ID)
        return when (option.value) {
            1 -> PredictionRequest.Gender.Male
            2 -> PredictionRequest.Gender.Female
            else -> PredictionRequest.Gender.Unspecified
        }
    }

    private fun optionValues(questionIds: List<Int>, answers: List<UserAnswer>): List<Int> =
        questionIds.map { id ->
            (answer(id, answers) as? AnswerValue.Option)?.option?.value ?: throw BuildError.MissingAnswer(id)
        }

    private fun answer(questionId: Int, answers: List<UserAnswer>): AnswerValue? =
        answers.lastOrNull { it.questionId == questionId }?.answerValue
}

@Serializable
data class PredictionResponse(
    val emotions: Map<String, Double>,
    val messages: List<Message>,
) {
    @Serializable
    data class Message(
        val text: String,
        val category: String,
        val score: Double,
        val author: String? = null,
    )

    fun probability(dimension: WellBeingDimension): Double? = emotions[dimension.rawValue]

    fun level(dimension: WellBeingDimension): Level? {
        val probability = probability(dimension) ?: return null
        return when {
            probability >= PRESENT_THRESHOLD -> Level.High
            probability >= LOW_THRESHOLD -> Level.Low
            else -> Level.No
        }
    }

    companion object {
        const val PRESENT_THRESHOLD = 0.5
        const val LOW_THRESHOLD = 0.25
    }
}

/** What a result is exported with (PDF, image). Holds no free text and no individual answers. */
data class ResultSummary(
    val dateMillis: Long,
    val language: AppLanguage,
    /** Levels only, in model order; percentages are deliberately left out. */
    val dimensions: List<DimensionResult>,
    val messages: List<PredictionResponse.Message>,
    /** Whether the support card was shown with the result. */
    val elevatedRisk: Boolean,
) {
    data class DimensionResult(val dimension: WellBeingDimension, val level: Level) {
        val tone: WellBeingDimension.Tone get() = dimension.tone(level)
    }

    companion object {
        fun of(prediction: PredictionResponse, dateMillis: Long, language: AppLanguage, elevatedRisk: Boolean) =
            ResultSummary(
                dateMillis = dateMillis,
                language = language,
                dimensions = WellBeingDimension.entries.mapNotNull { dimension ->
                    prediction.level(dimension)?.let { DimensionResult(dimension, it) }
                },
                messages = prediction.messages,
                elevatedRisk = elevatedRisk,
            )
    }
}

/**
 * The language of a text as the pipeline detects it. The Space picks the language of the messages from
 * the text alone, so the app refuses text the pipeline would read as another language.
 * Mirrors `detect_language` in the Space's app.py (and PipelineLanguage.swift). Keep them in step.
 */
enum class PipelineLanguage {
    Turkish,
    Persian,
    English,
    ;

    companion object {
        private val persianScalars = 0x0600..0x06FF
        private val turkishLetters = "ğüşıöçĞÜŞİÖÇ".codePoints().toArray().toSet()
        private val turkishWords = listOf("bir", "ve", "bu", "için", "çok", "ben", "sen", "değil", "var", "yok", "ama", "daha")

        fun detect(text: String): PipelineLanguage {
            val scalars = text.codePoints().toArray()
            val persianCount = scalars.count { it in persianScalars }
            if (persianCount.toDouble() / maxOf(scalars.size, 1) > 0.3) return Persian
            val hasTurkishLetter = scalars.any { it in turkishLetters }
            val padded = " ${text.lowercase()} "
            val turkishWordCount = turkishWords.count { padded.contains(" $it ") }
            return if (hasTurkishLetter || turkishWordCount >= 2) Turkish else English
        }

        fun of(language: AppLanguage): PipelineLanguage = when (language) {
            AppLanguage.Turkish -> Turkish
            AppLanguage.Persian -> Persian
        }
    }
}

/** User-perceived characters, as Swift's `String.count`. */
fun String.graphemeCount(): Int {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(this)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}
