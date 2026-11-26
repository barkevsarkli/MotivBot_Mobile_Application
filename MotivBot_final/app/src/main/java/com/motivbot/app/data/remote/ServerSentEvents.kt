package com.motivbot.app.data.remote

import com.motivbot.app.domain.MotivBotApiError
import com.motivbot.app.domain.MotivBotApiException
import com.motivbot.app.domain.PredictionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream

// Port of ServerSentEvents.swift.

/** A source of lines; null once the stream has ended. A dropped connection throws. */
fun interface LineStream {
    suspend fun next(): String?
}

/** Splits bytes into lines on "\n", dropping a trailing "\r". Keeps empty lines: they end an event. */
class LineSplitter {
    private val buffer = ByteArrayOutputStream()

    fun append(byte: Byte): String? {
        if (byte != '\n'.code.toByte()) {
            buffer.write(byte.toInt())
            return null
        }
        return takeLine()
    }

    fun finish(): String? = if (buffer.size() == 0) null else takeLine()

    private fun takeLine(): String {
        var bytes = buffer.toByteArray()
        if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes = bytes.copyOf(bytes.size - 1)
        buffer.reset()
        return bytes.toString(Charsets.UTF_8)
    }
}

data class ServerSentEvent(val event: String, val data: String)

/** Server-sent event parser (WHATWG): joins multi-line data, skips comments, dispatches on a blank line. */
class ServerSentEventParser {
    private var event = ""
    private val dataLines = mutableListOf<String>()

    fun consume(line: String): ServerSentEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field: String
        var value: String
        if (colon >= 0) {
            field = line.substring(0, colon)
            value = line.substring(colon + 1)
            if (value.startsWith(" ")) value = value.substring(1)
        } else {
            field = line
            value = ""
        }
        when (field) {
            "event" -> event = value
            "data" -> dataLines += value
        }
        return null
    }

    /** Dispatches an event left without a trailing blank line. */
    fun finish(): ServerSentEvent? = dispatch()

    private fun dispatch(): ServerSentEvent? {
        val result = if (event.isEmpty() && dataLines.isEmpty()) {
            null
        } else {
            ServerSentEvent(event = event.ifEmpty { "message" }, data = dataLines.joinToString("\n"))
        }
        event = ""
        dataLines.clear()
        return result
    }
}

/** Reads the Gradio event stream: the result on `complete`, an error on `error`; ignores heartbeats. */
class GradioResultReader {
    private val parser = ServerSentEventParser()

    fun consume(line: String): PredictionResponse? = parser.consume(line)?.let(::handle)

    fun finish(): PredictionResponse =
        parser.finish()?.let(::handle) ?: throw MotivBotApiException(MotivBotApiError.StreamEnded)

    private fun handle(event: ServerSentEvent): PredictionResponse? = when (event.event) {
        "complete" -> decodeResult(event.data)
        "error" -> throw MotivBotApiException(classifyError(event.data))
        else -> null
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun classifyError(payload: String): MotivBotApiError {
            val element = runCatching { json.parseToJsonElement(payload) }.getOrNull()
            if (element is JsonPrimitive && element.isString) {
                val message = element.content
                return if (message.startsWith("404")) MotivBotApiError.Http(404) else MotivBotApiError.InvalidInput(message)
            }
            if (element !is JsonObject) return MotivBotApiError.InvalidInput("")
            val message = element.stringOrEmpty("error")
            val title = element.stringOrEmpty("title")
            if ("$title $message".contains("quota", ignoreCase = true)) return MotivBotApiError.QuotaExceeded
            return MotivBotApiError.InvalidInput(message)
        }

        private fun JsonObject.stringOrEmpty(key: String): String =
            (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: ""

        /** `data` is `[markdown, structured]`; structured is an object or an escaped JSON string. */
        private fun decodeResult(payload: String): PredictionResponse {
            val array = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonArray
            if (array == null || array.size < 2) throw MotivBotApiException(MotivBotApiError.Decoding)
            val structured: JsonElement = when (val second = array[1]) {
                is JsonPrimitive -> if (second.isString) {
                    runCatching { json.parseToJsonElement(second.content) }.getOrNull()
                        ?: throw MotivBotApiException(MotivBotApiError.Decoding)
                } else {
                    throw MotivBotApiException(MotivBotApiError.Decoding)
                }
                is JsonObject -> second
                else -> throw MotivBotApiException(MotivBotApiError.Decoding)
            }
            return runCatching { json.decodeFromJsonElement(PredictionResponse.serializer(), structured) }
                .getOrElse { throw MotivBotApiException(MotivBotApiError.Decoding) }
        }
    }
}
