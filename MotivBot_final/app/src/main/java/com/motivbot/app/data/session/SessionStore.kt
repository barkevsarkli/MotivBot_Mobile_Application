package com.motivbot.app.data.session

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.domain.AnswerValue
import com.motivbot.app.domain.PredictionResponse
import com.motivbot.app.domain.TestSection
import com.motivbot.app.domain.UserAnswer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

// Port of SessionStore.swift: save & resume.

/** An answer as stored on disk. Options are stored by id and looked up in `TestData` on load. */
@Serializable
data class SavedAnswer(val sectionId: Int, val questionId: Int, val value: Value) {
    @Serializable
    sealed interface Value {
        @Serializable
        @SerialName("option")
        data class Option(val id: Int) : Value

        @Serializable
        @SerialName("scale")
        data class Scale(val value: Int) : Value

        @Serializable
        @SerialName("text")
        data class Text(val text: String) : Value
    }
}

/** An assessment in progress. Saved after every change and deleted once the results are shown. */
@Serializable
data class SavedSession(
    val version: Int = CURRENT_VERSION,
    val language: AppLanguage,
    val emotionText: String,
    val answers: List<SavedAnswer>,
    val stage: Stage,
    val sectionIndex: Int,
    val questionIndex: Int,
    /** Epoch milliseconds. */
    val startedAt: Long,
) {
    @Serializable
    enum class Stage {
        @SerialName("emotionInput")
        EmotionInput,

        @SerialName("questionnaire")
        Questionnaire,

        /** All questions were answered; the analysis had started. */
        @SerialName("analysis")
        Analysis,
    }

    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** The last result, kept until restart so it can still be exported after the app closes. */
@Serializable
data class SavedResult(
    val version: Int = CURRENT_VERSION,
    val language: AppLanguage,
    /** Epoch milliseconds. */
    val completedAt: Long,
    val prediction: PredictionResponse,
    val elevatedRisk: Boolean,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

val UserAnswer.saved: SavedAnswer
    get() = SavedAnswer(
        sectionId = sectionId,
        questionId = questionId,
        value = when (val value = answerValue) {
            is AnswerValue.Option -> SavedAnswer.Value.Option(value.option.id)
            is AnswerValue.Scale -> SavedAnswer.Value.Scale(value.value)
            is AnswerValue.Text -> SavedAnswer.Value.Text(value.text)
        },
    )

/** Null when the question or option no longer exists in [sections]. */
fun SavedAnswer.restore(sections: List<TestSection>): UserAnswer? {
    val question = sections.firstOrNull { it.id == sectionId }?.questions?.firstOrNull { it.id == questionId } ?: return null
    val answerValue: AnswerValue = when (val saved = value) {
        is SavedAnswer.Value.Option -> AnswerValue.Option(question.options?.firstOrNull { it.id == saved.id } ?: return null)
        is SavedAnswer.Value.Scale -> AnswerValue.Scale(saved.value)
        is SavedAnswer.Value.Text -> AnswerValue.Text(saved.text)
    }
    return UserAnswer(sectionId, questionId, answerValue)
}

interface SessionStore {
    /** The saved session, or null if there is none, it expired, or its version is unknown. */
    fun loadSession(): SavedSession?
    fun saveSession(session: SavedSession)
    fun deleteSession()
    fun loadResult(): SavedResult?
    fun saveResult(result: SavedResult)
    fun deleteResult()
}

/**
 * JSON files in the app's no-backup directory. Unreadable, expired and unknown-version files are
 * deleted instead of loaded.
 */
class FileSessionStore(
    private val directory: File,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) : SessionStore {

    private val sessionFile get() = File(directory, "session.json")
    private val resultFile get() = File(directory, "result.json")

    override fun loadSession(): SavedSession? {
        val session = load(sessionFile, SavedSession.CURRENT_VERSION, SavedSession.serializer()) ?: return null
        if (now() - session.startedAt > maxAgeMillis) {
            deleteSession()
            return null
        }
        return session
    }

    override fun saveSession(session: SavedSession) = save(session, sessionFile, SavedSession.serializer())
    override fun deleteSession() { sessionFile.delete() }
    override fun loadResult(): SavedResult? = load(resultFile, SavedResult.CURRENT_VERSION, SavedResult.serializer())
    override fun saveResult(result: SavedResult) = save(result, resultFile, SavedResult.serializer())
    override fun deleteResult() { resultFile.delete() }

    @Synchronized
    private fun <T> load(file: File, version: Int, serializer: KSerializer<T>): T? {
        if (!file.exists()) return null
        val value = runCatching {
            val text = file.readText(Charsets.UTF_8)
            val header = json.parseToJsonElement(text) as JsonObject
            if (header["version"]?.jsonPrimitive?.int != version) null else json.decodeFromString(serializer, text)
        }.getOrNull()
        if (value == null) file.delete()
        return value
    }

    @Synchronized
    private fun <T> save(value: T, file: File, serializer: KSerializer<T>) {
        // Saving is best effort; the contents are never logged.
        runCatching {
            directory.mkdirs()
            val temporary = File(directory, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
