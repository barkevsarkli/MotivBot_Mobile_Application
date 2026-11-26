package com.motivbot.app

import com.motivbot.app.data.remote.HttpRequest
import com.motivbot.app.data.remote.LineSplitter
import com.motivbot.app.data.remote.MotivBotApiService
import com.motivbot.app.data.remote.OkHttpTransport
import com.motivbot.app.data.remote.PredictionProgress
import com.motivbot.app.data.remote.ServerSentEvent
import com.motivbot.app.data.remote.ServerSentEventParser
import com.motivbot.app.domain.AnalysisError
import com.motivbot.app.domain.MotivBotApiError
import com.motivbot.app.domain.MotivBotApiException
import com.motivbot.app.domain.Level
import com.motivbot.app.domain.WellBeingDimension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

// Port of the server-sent event and service tests in IntegrationTests.swift.

class ServerSentEventTest {

    @Test
    fun joinsMultiLineDataAndSkipsComments() {
        val parser = ServerSentEventParser()
        val events = listOf(": keep-alive", "event:complete", "data: [1,", "data:  2]", "").mapNotNull(parser::consume)
        assertEquals(listOf(ServerSentEvent("complete", "[1,\n 2]")), events)
    }

    @Test
    fun dispatchesFinalEventWithoutTrailingBlankLine() {
        val parser = ServerSentEventParser()
        assertNull(parser.consume("event: complete"))
        assertNull(parser.consume("data: x"))
        assertEquals(ServerSentEvent("complete", "x"), parser.finish())
        assertNull(parser.finish())
    }

    @Test
    fun splitsCrlfAndKeepsEmptyLines() {
        val splitter = LineSplitter()
        val lines = "event: a\r\ndata: b\r\n\r\ntail".toByteArray().toList().mapNotNull(splitter::append)
        assertEquals(listOf("event: a", "data: b", ""), lines)
        assertEquals("tail", splitter.finish())
    }

    @Test
    fun decodesRecordedResult() {
        for (stream in listOf(RecordedStream.complete, RecordedStream.heartbeatThenComplete)) {
            val response = MotivBotApiService.parseEventStream(stream)
            assertEquals(3, response.messages.size)
            assertEquals(listOf("Matt Haig", "Geert Verschaeve", "Elizabeth Wurtzel"), response.messages.map { it.author })
            assertEquals(Level.High, response.level(WellBeingDimension.NegativeAffect))
            assertEquals("Kaygı sen değilsin.", response.messages[1].text)
        }
    }

    @Test
    fun completeWithoutTrailingBlankLine() {
        assertEquals(3, MotivBotApiService.parseEventStream(RecordedStream.complete.trimEnd('\n')).messages.size)
    }

    @Test
    fun classifiesRecordedFailures() {
        val cases = listOf(
            RecordedStream.quotaExceeded to MotivBotApiError.QuotaExceeded,
            RecordedStream.BELOW_MINIMUM to MotivBotApiError.InvalidInput("Value 5 is less than minimum value 10."),
            RecordedStream.NULL_ERROR to MotivBotApiError.InvalidInput(""),
            RecordedStream.UNKNOWN_EVENT to MotivBotApiError.Http(404),
            RecordedStream.ENDS_WITHOUT_COMPLETE to MotivBotApiError.StreamEnded,
        )
        for ((stream, expected) in cases) {
            val error = assertThrows(MotivBotApiException::class.java) { MotivBotApiService.parseEventStream(stream) }
            assertEquals(expected, error.error)
        }
    }

    @Test
    fun invalidChoiceIsInvalidInput() {
        val error = assertThrows(MotivBotApiException::class.java) { MotivBotApiService.parseEventStream(RecordedStream.invalidChoice) }
        val message = (error.error as MotivBotApiError.InvalidInput).message
        assertTrue(message, message.contains("Non-binary is not in the list of choices"))
    }
}

class MotivBotApiServiceTest {

    private fun makeService(transport: FakeTransport, time: FakeTime = FakeTime(), isOnline: Boolean = true) =
        MotivBotApiService(
            baseUrl = "https://example.test",
            transport = transport,
            timing = time.timing,
            isOnline = { isOnline },
        )

    private suspend fun expectFailure(block: suspend () -> Unit): MotivBotApiError {
        try {
            block()
        } catch (exception: MotivBotApiException) {
            return exception.error
        }
        fail("expected a MotivBotApiException")
        error("unreachable")
    }

    @Test
    fun submitsNamedParametersThenStreamsResult() = runTest {
        val transport = FakeTransport()
        val progress = mutableListOf<PredictionProgress>()

        val response = makeService(transport).predict(Fixture.validRequest()) { progress += it }

        assertEquals(3, response.messages.size)
        assertEquals(listOf(PredictionProgress.Connecting, PredictionProgress.Analysing), progress)
        assertEquals("https://example.test/gradio_api/call/v2/run_pipeline", transport.lastSubmit?.url)
        assertEquals("POST", transport.lastSubmit?.method)
        assertEquals("https://example.test/gradio_api/call/run_pipeline/abc123", transport.lastStream?.url)
        val body = Json.parseToJsonElement(transport.lastSubmit!!.body!!.toString(Charsets.UTF_8)).jsonObject
        assertEquals(setOf("text", "age", "gender", "phq9", "rses"), body.keys)
    }

    @Test
    fun retriesWhileSpaceStartsWithExponentialBackoff() = runTest {
        val transport = FakeTransport()
        transport.submitReplies = mutableListOf(
            FakeTransport.SubmitReply.Status(503, ""),
            FakeTransport.SubmitReply.Status(503, ""),
            FakeTransport.SubmitReply.Status(429, ""),
            FakeTransport.SubmitReply.Accepted,
        )
        val time = FakeTime()
        val progress = mutableListOf<PredictionProgress>()

        makeService(transport, time).predict(Fixture.validRequest()) { progress += it }

        assertEquals(4, transport.submitCount)
        assertEquals(listOf(1_000L, 2_000L, 4_000L), time.sleeps)
        assertTrue(PredictionProgress.WakingUp in progress)
    }

    @Test
    fun givesUpWhenRetryBudgetIsSpent() = runTest {
        val transport = FakeTransport()
        transport.submitReplies = mutableListOf(FakeTransport.SubmitReply.Status(503, ""))
        val time = FakeTime()

        val error = expectFailure { makeService(transport, time).predict(Fixture.validRequest()) {} }

        assertEquals(MotivBotApiError.SpaceStarting, error)
        // 1 + 2 + 4 + 8 + 16 + 16 + 16 + 16 = 79 s; one more 16 s wait would pass the 90 s budget.
        assertEquals(listOf(1, 2, 4, 8, 16, 16, 16, 16).map { it * 1_000L }, time.sleeps)
        assertEquals(9, transport.submitCount)
    }

    @Test
    fun jitterStaysWithinAQuarterOfTheBase() {
        val policy = MotivBotApiService.RetryPolicy()
        assertEquals(3_000L, policy.delayMillis(afterAttempt = 3, random = 0.0))
        assertEquals(4_000L, policy.delayMillis(afterAttempt = 3, random = 0.5))
        assertEquals(5_000L, policy.delayMillis(afterAttempt = 3, random = 1.0))
        assertEquals(16_000L, policy.delayMillis(afterAttempt = 20, random = 0.5))
    }

    @Test
    fun retriesTransientNetworkErrorsAndDroppedStreams() = runTest {
        val transport = FakeTransport()
        transport.submitReplies = mutableListOf(
            FakeTransport.SubmitReply.Failure(SocketTimeoutException()),
            FakeTransport.SubmitReply.Accepted,
        )
        transport.streamReplies = mutableListOf(
            FakeTransport.StreamReply.Dropped("event: heartbeat\ndata: null\n\n", IOException("connection lost")),
            FakeTransport.StreamReply.Events(RecordedStream.ENDS_WITHOUT_COMPLETE),
            FakeTransport.StreamReply.Events(RecordedStream.complete),
        )
        val time = FakeTime()

        makeService(transport, time).predict(Fixture.validRequest()) {}

        assertEquals(4, transport.submitCount)
        assertEquals(3, transport.streamCount)
        assertEquals(3, time.sleeps.size)
    }

    @Test
    fun doesNotRetryPermanentFailures() = runTest {
        val cases = listOf(
            Triple(FakeTransport.SubmitReply.Status(422, "bad"), true, MotivBotApiError.InvalidInput("bad")),
            Triple(FakeTransport.SubmitReply.Status(500, ""), true, MotivBotApiError.Http(500)),
            Triple(FakeTransport.SubmitReply.Failure(UnknownHostException()), false, MotivBotApiError.Offline),
            Triple(
                FakeTransport.SubmitReply.Failure(SSLHandshakeException("bad certificate")),
                true,
                MotivBotApiError.Network(MotivBotApiError.NetworkKind.Unknown),
            ),
        )
        for ((reply, online, expected) in cases) {
            val transport = FakeTransport()
            transport.submitReplies = mutableListOf(reply)
            val error = expectFailure { makeService(transport, isOnline = online).predict(Fixture.validRequest()) {} }
            assertEquals(expected, error)
            assertEquals(1, transport.submitCount)
        }
    }

    @Test
    fun doesNotRetryExhaustedQuota() = runTest {
        val transport = FakeTransport()
        transport.streamReplies = mutableListOf(FakeTransport.StreamReply.Events(RecordedStream.quotaExceeded))
        val error = expectFailure { makeService(transport).predict(Fixture.validRequest()) {} }
        assertEquals(MotivBotApiError.QuotaExceeded, error)
        assertEquals(1, transport.submitCount)
        assertEquals(AnalysisError.ServiceBusy, MotivBotApiError.QuotaExceeded.analysisError)
    }

    @Test
    fun validatesBeforeSending() = runTest {
        val transport = FakeTransport()
        expectFailure { makeService(transport).predict(Fixture.validRequest(text = "  kısa  ")) {} }
        assertEquals(0, transport.submitCount)
    }

    @Test
    fun stopsImmediatelyWhenCancelled() = runTest {
        val transport = FakeTransport()
        transport.submitReplies = mutableListOf(FakeTransport.SubmitReply.Hang)
        val service = makeService(transport)
        var outcome: Throwable? = null
        val job = launch {
            try {
                service.predict(Fixture.validRequest()) {}
            } catch (error: Throwable) {
                outcome = error
                throw error
            }
        }
        runCurrent()
        job.cancel()
        job.join()

        assertTrue("$outcome", outcome is CancellationException)
        assertEquals(1, transport.submitCount)
    }

    @Test
    fun mapsTransportExceptionsToNetworkKinds() {
        assertEquals(MotivBotApiError.NetworkKind.TimedOut, MotivBotApiService.networkKind(SocketTimeoutException()))
        assertEquals(MotivBotApiError.NetworkKind.CannotFindHost, MotivBotApiService.networkKind(UnknownHostException()))
        assertEquals(MotivBotApiError.NetworkKind.ConnectionLost, MotivBotApiService.networkKind(IOException()))
        assertEquals(MotivBotApiError.NetworkKind.Unknown, MotivBotApiService.networkKind(SSLHandshakeException("x")))
    }

    @Test
    fun prewarmRunsOnePingAtATime() = runTest {
        val transport = FakeTransport()
        transport.infoDelayMillis = 100
        val service = makeService(transport)

        val first = async { service.prewarm() }
        val second = async { service.prewarm() }
        first.await()
        second.await()
        assertEquals(1, transport.infoCount)

        service.prewarm()
        assertEquals(2, transport.infoCount)
    }
}

/** The real OkHttp transport against a local server. */
class OkHttpTransportTest {

    private fun request(server: MockWebServer) = HttpRequest(url = server.url("/stream").toString(), timeoutMillis = 5_000)

    @Test
    fun streamsLinesAndKeepsEmptyOnes() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody("event: a\r\ndata: b\r\n\r\ntail"))
            val (status, lines) = OkHttpTransport(OkHttpClient()).lines(request(server))
            val received = mutableListOf<String>()
            while (true) received += lines.next() ?: break
            assertEquals(200, status)
            assertEquals(listOf("event: a", "data: b", "", "tail"), received)
            assertNull(lines.next())
        }
    }

    @Test
    fun droppedConnectionThrows() = runTest {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setBody("event: heartbeat\ndata: null\n\n" + "x".repeat(64 * 1024))
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
            )
            val (_, lines) = OkHttpTransport(OkHttpClient()).lines(request(server))
            try {
                while (lines.next() != null) Unit
                fail("expected the dropped connection to throw")
            } catch (_: IOException) {
            }
        }
    }

    @Test
    fun errorStatusHasNoLines() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("starting"))
            val (status, lines) = OkHttpTransport(OkHttpClient()).lines(request(server))
            assertEquals(503, status)
            assertNull(lines.next())
        }
    }

    @Test
    fun postsBodyWithHeaders() = runTest {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"event_id":"abc"}"""))
            val response = OkHttpTransport(OkHttpClient()).data(
                HttpRequest(
                    url = server.url("/submit").toString(),
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = """{"a":1}""".toByteArray(),
                    timeoutMillis = 5_000,
                ),
            )
            assertEquals(200, response.status)
            assertEquals("""{"event_id":"abc"}""", response.body.toString(Charsets.UTF_8))
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("""{"a":1}""", recorded.body.readUtf8())
            assertEquals("no-store", recorded.getHeader("Cache-Control"))
        }
    }
}
