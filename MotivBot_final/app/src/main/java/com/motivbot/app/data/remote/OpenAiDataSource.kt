package com.motivbot.app.data.remote

import com.motivbot.app.core.AppError
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.AppResult
import com.motivbot.app.data.remote.dto.ChatMessageDto
import com.motivbot.app.data.remote.dto.ChatRequestDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of `OpenAIService.sendToOpenAI(prompt:)`.
 *
 * Differences from the Swift original, all deliberate:
 *  - the API key is no longer a source literal; it is attached by [AuthInterceptor] from
 *    `BuildConfig`, so it is not visible in this file at all;
 *  - the 60 s timeout moved to the OkHttp client (see `NetworkModule`), where it also covers
 *    connect and read phases, not just the overall request as `timeoutInterval` did;
 *  - errors are returned as [AppResult.Failure] instead of thrown.
 */
@Singleton
class OpenAiDataSource @Inject constructor(
    private val api: OpenAiApi,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun fetchMotivation(
        prompt: String,
        language: AppLanguage,
    ): AppResult<String> = withContext(ioDispatcher) {
        val request = ChatRequestDto(
            model = MODEL,
            messages = listOf(
                ChatMessageDto(role = ROLE_SYSTEM, content = systemPrompt(language)),
                ChatMessageDto(role = ROLE_USER, content = prompt),
            ),
            maxTokens = MAX_TOKENS,
            temperature = TEMPERATURE,
        )

        try {
            val response = api.createChatCompletion(request)

            if (!response.isSuccessful) {
                val body = response.errorBody()?.string().orEmpty().ifBlank { "Unknown error" }
                return@withContext AppResult.Failure(AppError.Api(response.code(), body))
            }

            val content = response.body()
                ?.choices
                ?.firstOrNull()
                ?.message
                ?.content
                ?.trim()

            if (content.isNullOrEmpty()) {
                AppResult.Failure(AppError.NoContent)
            } else {
                AppResult.Success(content)
            }
        } catch (io: IOException) {
            // Timeouts, DNS failures, connection resets — Swift surfaced these as thrown URLErrors.
            AppResult.Failure(AppError.Network(io))
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Unknown(t))
        }
    }

    /**
     * The Swift `systemMessage`, plus an explicit language directive.
     *
     * The original always asked in English and never told the model what language to answer in, so
     * a Turkish or Persian user got their questionnaire back as an English quote. The instruction
     * is stated first and repeated as a hard constraint at the end — models follow a
     * language directive far more reliably when it brackets the task description.
     */
    private fun systemPrompt(language: AppLanguage): String {
        val name = language.promptName
        val script = if (language.isRtl) {
            " Write in the Persian script, right-to-left, using Persian (not Arabic) digits."
        } else {
            ""
        }

        return """
            You MUST write your entire response in $name.$script

            You are a compassionate and insightful psychological well-being assistant. Based on the
            user's emotional expression and questionnaire responses, provide a personalized,
            uplifting motivational quote followed by a brief (2-3 sentence) supportive message. Be
            warm, empathetic, and encouraging. Do not diagnose or prescribe — only offer emotional
            support and positive reinforcement.

            The questionnaire below may reach you in $name or in English; either way, your answer
            must be entirely in $name. Use natural, fluent phrasing that a native speaker would
            use — do not translate literally. If you quote a non-$name author, give the quote in
            $name and leave only the author's name in its original spelling. Do not include any
            English words, transliteration, or explanatory notes.
        """.trimIndent()
    }

    private companion object {
        const val MODEL = "gpt-4o-mini"
        const val MAX_TOKENS = 500
        const val TEMPERATURE = 0.8
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
    }
}
