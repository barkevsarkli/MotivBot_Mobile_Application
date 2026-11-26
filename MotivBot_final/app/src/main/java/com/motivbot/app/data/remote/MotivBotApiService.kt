package com.motivbot.app.data.remote

import com.motivbot.app.domain.MotivBotApiError
import com.motivbot.app.domain.MotivBotApiException
import com.motivbot.app.domain.PredictionRequest
import com.motivbot.app.domain.PredictionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

// Port of MotivBotAPIService.swift.

enum class PredictionProgress { Connecting, WakingUp, Analysing }

interface PredictionService {
    suspend fun predict(request: PredictionRequest, progress: (PredictionProgress) -> Unit): PredictionResponse

    /** Pings the Space so it starts booting. Runs at most one ping at a time. */
    suspend fun prewarm()
}

class MotivBotApiService(
    private val baseUrl: String,
    private val transport: HttpTransport,
    private val policy: RetryPolicy = RetryPolicy(),
    private val timing: Timing = Timing.Live,
    /** False when the device has no network; such failures are not retried. */
    private val isOnline: () -> Boolean = { true },
) : PredictionService {

    data class RetryPolicy(
        val initialDelayMillis: Long = 1_000,
        val maxDelayMillis: Long = 16_000,
        /** No retry starts after this much time. */
        val budgetMillis: Long = 90_000,
        /** The whole analysis gives up after this long. */
        val deadlineMillis: Long = 150_000,
        val requestTimeoutMillis: Long = 30_000,
        val prewarmTimeoutMillis: Long = 90_000,
    ) {
        /** Exponential backoff with ±25 % jitter. */
        fun delayMillis(afterAttempt: Int, random: Double): Long {
            val exponential = initialDelayMillis shl minOf(afterAttempt - 1, 16)
            val base = minOf(exponential, maxDelayMillis)
            return (base * (0.75 + 0.5 * random)).toLong()
        }
    }

    class Timing(
        val now: () -> Long,
        val sleep: suspend (Long) -> Unit,
        val random: () -> Double,
    ) {
        companion object {
            val Live = Timing(
                now = { System.nanoTime() / 1_000_000 },
                sleep = { delay(it) },
                random = { Random.nextDouble() },
            )
        }
    }

    private val prewarmLock = Mutex()

    override suspend fun prewarm() {
        if (prewarmLock.isLocked) {
            prewarmLock.withLock { }
            return
        }
        prewarmLock.withLock {
            try {
                transport.data(HttpRequest(url = url(INFO_PATH), timeoutMillis = policy.prewarmTimeoutMillis))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Best effort.
            }
        }
    }

    override suspend fun predict(request: PredictionRequest, progress: (PredictionProgress) -> Unit): PredictionResponse {
        request.validate()
        val start = timing.now()
        progress(PredictionProgress.Connecting)
        var attempt = 0
        while (true) {
            attempt++
            coroutineContext.ensureActive()
            try {
                return runOnce(request, start, progress)
            } catch (exception: MotivBotApiException) {
                val error = exception.error
                if (!error.isRetryable) throw exception
                val delayMillis = policy.delayMillis(attempt, timing.random())
                if (timing.now() - start + delayMillis > policy.budgetMillis) throw exception
                if (error == MotivBotApiError.SpaceStarting) progress(PredictionProgress.WakingUp)
                timing.sleep(delayMillis)
            }
        }
    }

    private suspend fun runOnce(body: PredictionRequest, start: Long, progress: (PredictionProgress) -> Unit): PredictionResponse {
        try {
            val submit = transport.data(
                HttpRequest(
                    url = url(SUBMIT_PATH),
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = GradioResultReader.json.encodeToString(PredictionRequest.WireBody.serializer(), body.toWire())
                        .toByteArray(Charsets.UTF_8),
                    timeoutMillis = policy.requestTimeoutMillis,
                ),
            )
            check(submit.status, submit.body)
            val eventId = runCatching {
                GradioResultReader.json.decodeFromString(EventId.serializer(), submit.body.toString(Charsets.UTF_8)).event_id
            }.getOrElse { throw MotivBotApiException(MotivBotApiError.Decoding) }

            progress(PredictionProgress.Analysing)

            val (status, lines) = transport.lines(
                HttpRequest(
                    url = "${url(RESULT_PATH)}/$eventId",
                    headers = mapOf("Accept" to "text/event-stream"),
                    timeoutMillis = policy.requestTimeoutMillis,
                ),
            )
            check(status, null)
            val reader = GradioResultReader()
            while (true) {
                val line = lines.next() ?: break
                if (timing.now() - start > policy.deadlineMillis) throw MotivBotApiException(MotivBotApiError.DeadlineExceeded)
                reader.consume(line)?.let { return it }
            }
            return reader.finish()
        } catch (exception: Exception) {
            throw mapTransportError(exception)
        }
    }

    private fun url(path: String) = "${baseUrl.trimEnd('/')}/$path"

    /** Maps transport failures to [MotivBotApiException]; keeps cancellation as it is. */
    private fun mapTransportError(error: Exception): Exception {
        if (error is MotivBotApiException || error is CancellationException) return error
        if (!isOnline()) return MotivBotApiException(MotivBotApiError.Offline)
        return MotivBotApiException(MotivBotApiError.Network(networkKind(error)))
    }

    @Suppress("PropertyName")
    @Serializable
    private data class EventId(val event_id: String)

    companion object {
        const val DEFAULT_BASE_URL = "https://flightmaestro-motivbot-pipeline.hf.space"
        const val SUBMIT_PATH = "gradio_api/call/v2/run_pipeline"
        const val RESULT_PATH = "gradio_api/call/run_pipeline"
        const val INFO_PATH = "gradio_api/info"

        fun check(status: Int, body: ByteArray?) {
            val error = when (status) {
                in 200..299 -> return
                429 -> MotivBotApiError.RateLimited
                502, 503, 504 -> MotivBotApiError.SpaceStarting
                in 400..499 -> MotivBotApiError.InvalidInput(body?.toString(Charsets.UTF_8) ?: "")
                else -> MotivBotApiError.Http(status)
            }
            throw MotivBotApiException(error)
        }

        fun networkKind(error: Exception): MotivBotApiError.NetworkKind = when (error) {
            is SocketTimeoutException -> MotivBotApiError.NetworkKind.TimedOut
            is UnknownHostException -> MotivBotApiError.NetworkKind.CannotFindHost
            is ConnectException -> MotivBotApiError.NetworkKind.CannotConnect
            is InterruptedIOException -> MotivBotApiError.NetworkKind.TimedOut
            is SSLException -> MotivBotApiError.NetworkKind.Unknown
            is IOException -> MotivBotApiError.NetworkKind.ConnectionLost
            else -> MotivBotApiError.NetworkKind.Unknown
        }

        /** Parses a whole recorded event stream. Used by the tests. */
        fun parseEventStream(text: String): PredictionResponse {
            val reader = GradioResultReader()
            val splitter = LineSplitter()
            for (byte in text.toByteArray(Charsets.UTF_8)) {
                splitter.append(byte)?.let { line -> reader.consume(line)?.let { return it } }
            }
            splitter.finish()?.let { line -> reader.consume(line)?.let { return it } }
            return reader.finish()
        }
    }
}
