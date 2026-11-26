package com.motivbot.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val timeoutMillis: Long,
)

class HttpResponse(val status: Int, val body: ByteArray)

/** The network seam of [MotivBotApiService]; tests replace it with a fake. */
interface HttpTransport {
    suspend fun data(request: HttpRequest): HttpResponse

    /** Opens a streaming GET and returns the status and the body as lines. */
    suspend fun lines(request: HttpRequest): Pair<Int, LineStream>
}

class OkHttpTransport(private val client: OkHttpClient) : HttpTransport {

    override suspend fun data(request: HttpRequest): HttpResponse {
        val response = execute(request)
        return response.use { HttpResponse(it.code, it.body?.bytes() ?: ByteArray(0)) }
    }

    override suspend fun lines(request: HttpRequest): Pair<Int, LineStream> {
        val response = execute(request)
        if (!response.isSuccessful) {
            response.close()
            return response.code to LineStream { null }
        }
        val source = response.body?.source() ?: run {
            response.close()
            return response.code to LineStream { null }
        }
        var closed = false
        return response.code to LineStream {
            if (closed) return@LineStream null
            try {
                withContext(Dispatchers.IO) { source.readUtf8Line() }.also { if (it == null) { closed = true; response.close() } }
            } catch (error: Throwable) {
                closed = true
                response.close()
                throw error
            }
        }
    }

    private suspend fun execute(request: HttpRequest): Response {
        val call = client.newBuilder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .readTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(request.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request.toOkHttp())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { response.close() }
                }
            })
        }
    }

    private fun HttpRequest.toOkHttp(): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        val requestBody = body?.toRequestBody(headers["Content-Type"]?.toMediaTypeOrNull())
        builder.method(method, requestBody)
        // Results are sensitive; keep them out of any cache.
        builder.header("Cache-Control", "no-store")
        return builder.build()
    }
}
