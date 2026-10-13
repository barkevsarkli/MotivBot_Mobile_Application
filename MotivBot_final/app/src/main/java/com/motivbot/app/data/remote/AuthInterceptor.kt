package com.motivbot.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named

/**
 * Attaches the `Authorization: Bearer …` and `Content-Type` headers that
 * `OpenAIService.sendToOpenAI` set by hand on every `URLRequest`.
 *
 * Centralising it here means no call site can forget the header, and the key itself stays out of
 * the business logic.
 */
class AuthInterceptor @Inject constructor(
    @Named("openAiApiKey") private val apiKey: String,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .header("Content-Type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
