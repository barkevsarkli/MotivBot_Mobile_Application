package com.motivbot.app.data.remote

import com.motivbot.app.data.remote.dto.ChatRequestDto
import com.motivbot.app.data.remote.dto.ChatResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit description of the single endpoint the Swift app talked to
 * (`POST https://api.openai.com/v1/chat/completions`).
 *
 * Returning `Response<T>` rather than the bare body keeps the status code available, which the
 * Swift code needed for its `.apiError(statusCode:message:)` case. The `suspend` modifier gives
 * us the same `async/await` call shape as `URLSession.data(for:)` with no callbacks.
 */
interface OpenAiApi {

    @POST("v1/chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatRequestDto,
    ): Response<ChatResponseDto>
}
