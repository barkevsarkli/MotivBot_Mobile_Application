package com.motivbot.app

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.StringProvider
import com.motivbot.app.data.remote.GradioResultReader
import com.motivbot.app.data.remote.HttpRequest
import com.motivbot.app.data.remote.HttpResponse
import com.motivbot.app.data.remote.HttpTransport
import com.motivbot.app.data.remote.LineSplitter
import com.motivbot.app.data.remote.LineStream
import com.motivbot.app.data.remote.MotivBotApiService
import com.motivbot.app.data.remote.PredictionProgress
import com.motivbot.app.data.remote.PredictionService
import com.motivbot.app.data.session.SavedResult
import com.motivbot.app.data.session.SavedSession
import com.motivbot.app.data.session.SessionStore
import com.motivbot.app.domain.AnswerOption
import com.motivbot.app.domain.AnswerValue
import com.motivbot.app.domain.MotivBotApiError
import com.motivbot.app.domain.MotivBotApiException
import com.motivbot.app.domain.PredictionRequest
import com.motivbot.app.domain.PredictionResponse
import com.motivbot.app.domain.TestData
import com.motivbot.app.domain.UserAnswer
import com.motivbot.app.ui.CsvSink
import com.motivbot.app.ui.LocalizationManager
import com.motivbot.app.ui.WellBeingDependencies
import com.motivbot.app.ui.WellBeingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.coroutines.coroutineContext

// Shared fixtures and fakes. Port of the fixtures in MotivBotTests (PredictionTests.swift, IntegrationTests.swift).

object Fixture {
    val phq9 = listOf(1, 2, 1, 0, 2, 1, 1, 2, 0)
    val rses = listOf(3, 2, 1, 2, 0, 3, 2, 1, 1, 0)

    const val TURKISH_TEXT = "Herkes beni sinirlendiriyor, artık tahammülüm kalmadı"
    const val PERSIAN_TEXT = "امروز خیلی خسته و نگران هستم"

    fun option(questionId: Int, value: Int): AnswerOption =
        TestData.question(questionId)!!.options!!.first { it.value == value }

    /** A fully answered questionnaire, in the order the app records it. */
    fun answers(
        age: String = "25",
        genderValue: Int = 1,
        phq9: List<Int> = Fixture.phq9,
        attentionCheck: Int = 0,
        rses: List<Int> = Fixture.rses,
    ): List<UserAnswer> = buildList {
        add(UserAnswer(1, 1, AnswerValue.Text(age)))
        add(UserAnswer(1, 2, AnswerValue.Option(option(2, genderValue))))
        phq9.forEachIndexed { index, value -> add(UserAnswer(2, 7 + index, AnswerValue.Option(option(7 + index, value)))) }
        add(UserAnswer(2, 16, AnswerValue.Option(option(16, attentionCheck))))
        for (id in 17..23) add(UserAnswer(3, id, AnswerValue.Option(option(id, 2))))
        rses.forEachIndexed { index, value -> add(UserAnswer(4, 24 + index, AnswerValue.Option(option(24 + index, value)))) }
    }

    /** The structured JSON of `data[1]`, as returned by the pipeline. */
    val structuredJson = """{"emotions": {"Anxiety_Fear_Worry": 0.88, "Depression_Hopelessness_Sadness": 0.62, "Self_Esteem_Issue": 0.19, "Positive_Affect": 0.03, "Negative_Affect": 0.97}, "messages": [{"category": "Emotional Validation", "author": "Matt Haig", "score": 2.381, "text": "Hiçbir şey sonsuza kadar sürmez."}, {"category": "Cognitive Reframing", "score": 2.501, "text": "Kaygı sen değilsin."}, {"category": "Action-Oriented Encouragement", "score": 2.501, "text": "Korkunun üstesinden gelebilirsin."}]}"""

    fun response(): PredictionResponse =
        GradioResultReader.json.decodeFromString(PredictionResponse.serializer(), structuredJson)

    fun response(depression: Double) = PredictionResponse(
        emotions = mapOf(
            "Anxiety_Fear_Worry" to 0.1, "Depression_Hopelessness_Sadness" to depression,
            "Self_Esteem_Issue" to 0.1, "Positive_Affect" to 0.6, "Negative_Affect" to 0.2,
        ),
        messages = emptyList(),
    )

    fun validRequest(text: String = "Bugün çok yorgun ve endişeli hissediyorum.") =
        PredictionRequest(text = text, age = 25, gender = PredictionRequest.Gender.Female, phq9Items = phq9, rsesItems = rses)
}

/** Event streams recorded from the Space (message texts shortened). */
object RecordedStream {
    private val completePayload = """["Detected Language: Turkish\n\n Emotion Scores\n- **Anxiety_Fear_Worry**: 0.887", {"emotions": {"Anxiety_Fear_Worry": 0.8871341347694397, "Depression_Hopelessness_Sadness": 0.758378267288208, "Self_Esteem_Issue": 0.3113669753074646, "Positive_Affect": 0.03797024488449097, "Negative_Affect": 0.9802320003509521}, "messages": [{"category": "Emotional Validation", "author": "Matt Haig", "score": 2.5, "text": "Bir gün küçük bir şey için coşkulu gözyaşları dökeceksin."}, {"category": "Cognitive Reframing", "author": "Geert Verschaeve", "score": 2.646, "text": "Kaygı sen değilsin."}, {"category": "Action-Oriented Encouragement", "author": "Elizabeth Wurtzel", "score": 2.646, "text": "Kelimelerin güçlü olduğuna inanıyorum."}]}]"""

    val complete = "event: complete\ndata: $completePayload\n\n"

    val heartbeatThenComplete = "event: heartbeat\ndata: null\n\nevent: generating\ndata: null\n\n$complete"

    val quotaExceeded = "event: error\ndata: " +
        """{"error": "You have exceeded your ZeroGPU runs limit. Authenticate with a Hugging Face token for more quota - https://huggingface.co/settings/tokens", "duration": 10, "visible": true, "title": "ZeroGPU quota exceeded"}""" +
        "\n\n"

    val invalidChoice = "event: error\ndata: " +
        """{"error": "Value: Non-binary is not in the list of choices: ['Erkek', 'Kadın', 'Male', 'Female', 'مرد', 'زن']", "duration": 10, "visible": true, "title": "Error"}""" +
        "\n\n"

    const val BELOW_MINIMUM = "event: error\ndata: {\"error\": \"Value 5 is less than minimum value 10.\", \"duration\": 10, \"visible\": true, \"title\": \"Error\"}\n\n"
    const val NULL_ERROR = "event: error\ndata: {\"error\": null}\n\n"
    const val UNKNOWN_EVENT = "event: error\ndata: \"404: Not Found\"\n\n"
    const val ENDS_WITHOUT_COMPLETE = "event: heartbeat\ndata: null\n\nevent: generating\ndata: null\n\n"
}

/** Lines from a fixed text; throws [failure] once the text runs out, to simulate a dropped connection. */
class TextLineStream(text: String, private val failure: Exception? = null) : LineStream {
    private val bytes = text.toByteArray(Charsets.UTF_8)
    private var index = 0
    private val splitter = LineSplitter()
    private var finished = false

    override suspend fun next(): String? {
        while (index < bytes.size) {
            splitter.append(bytes[index++])?.let { return it }
        }
        if (!finished) {
            finished = true
            splitter.finish()?.let { return it }
        }
        failure?.let { throw it }
        return null
    }
}

class FakeTransport : HttpTransport {
    sealed interface SubmitReply {
        data class Status(val code: Int, val body: String) : SubmitReply
        data class Failure(val exception: Exception) : SubmitReply

        /** Waits until cancelled. */
        data object Hang : SubmitReply

        companion object {
            val Accepted = Status(200, """{"event_id":"abc123"}""")
        }
    }

    sealed interface StreamReply {
        data class Events(val text: String) : StreamReply
        data class Dropped(val text: String, val exception: Exception) : StreamReply
        data class Status(val code: Int) : StreamReply
    }

    /** Replies used in order; the last one repeats. */
    var submitReplies: MutableList<SubmitReply> = mutableListOf(SubmitReply.Accepted)
    var streamReplies: MutableList<StreamReply> = mutableListOf(StreamReply.Events(RecordedStream.complete))
    var infoDelayMillis = 0L

    var submitCount = 0
        private set
    var streamCount = 0
        private set
    var infoCount = 0
        private set
    var lastSubmit: HttpRequest? = null
        private set
    var lastStream: HttpRequest? = null
        private set

    override suspend fun data(request: HttpRequest): HttpResponse {
        if (request.url.endsWith(MotivBotApiService.INFO_PATH)) {
            infoCount++
            delay(infoDelayMillis)
            return HttpResponse(200, "{}".toByteArray())
        }
        submitCount++
        lastSubmit = request
        return when (val reply = next(submitReplies)) {
            is SubmitReply.Status -> HttpResponse(reply.code, reply.body.toByteArray())
            is SubmitReply.Failure -> throw reply.exception
            SubmitReply.Hang -> awaitCancellation()
        }
    }

    override suspend fun lines(request: HttpRequest): Pair<Int, LineStream> {
        streamCount++
        lastStream = request
        return when (val reply = next(streamReplies)) {
            is StreamReply.Events -> 200 to TextLineStream(reply.text)
            is StreamReply.Dropped -> 200 to TextLineStream(reply.text, reply.exception)
            is StreamReply.Status -> reply.code to LineStream { null }
        }
    }

    private fun <T> next(replies: MutableList<T>): T = if (replies.size > 1) replies.removeAt(0) else replies[0]
}

/** Time that only moves when the service sleeps. */
class FakeTime {
    var instant = 1_000_000L
    val sleeps = mutableListOf<Long>()

    val timing = MotivBotApiService.Timing(
        now = { instant },
        sleep = { millis ->
            coroutineContext.ensureActive()
            sleeps += millis
            instant += millis
        },
        random = { 0.5 },
    )
}

class InMemorySessionStore : SessionStore {
    var session: SavedSession? = null
    var result: SavedResult? = null

    override fun loadSession() = session
    override fun saveSession(session: SavedSession) { this.session = session }
    override fun deleteSession() { session = null }
    override fun loadResult() = result
    override fun saveResult(result: SavedResult) { this.result = result }
    override fun deleteResult() { result = null }
}

class StubService(results: List<Result<PredictionResponse>>) : PredictionService {
    /** Results returned in order; the last one repeats. */
    private val results = results.toMutableList()
    val sentRequests = mutableListOf<PredictionRequest>()
    var prewarmCount = 0
        private set

    override suspend fun predict(request: PredictionRequest, progress: (PredictionProgress) -> Unit): PredictionResponse {
        sentRequests += request
        progress(PredictionProgress.Analysing)
        val result = if (results.size > 1) results.removeAt(0) else results[0]
        return result.getOrThrow()
    }

    override suspend fun prewarm() {
        prewarmCount++
    }

    companion object {
        fun success(response: PredictionResponse) = StubService(listOf(Result.success(response)))
        fun failure(error: MotivBotApiError) = StubService(listOf(Result.failure(MotivBotApiException(error))))
    }
}

/** Resolves every string to its resource name's id, so CSV tests read only the fixed English parts. */
object FakeStrings : StringProvider {
    override fun getString(language: AppLanguage, resId: Int) = "s$resId"
}

class MemoryCsvSink : CsvSink {
    var csv: String? = null

    override fun write(csv: String): Boolean {
        this.csv = csv
        return true
    }

    override fun delete() {
        csv = null
    }
}

fun makeViewModel(
    service: PredictionService = StubService.failure(MotivBotApiError.Decoding),
    store: SessionStore = InMemorySessionStore(),
    csvSink: CsvSink = MemoryCsvSink(),
    now: () -> Long = System::currentTimeMillis,
) = WellBeingViewModel(
    WellBeingDependencies(
        service = service,
        store = store,
        strings = FakeStrings,
        csvSink = csvSink,
        localization = LocalizationManager(),
        now = now,
    ),
)

/** Runs `viewModelScope` (Dispatchers.Main) on a test dispatcher that starts coroutines eagerly. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
