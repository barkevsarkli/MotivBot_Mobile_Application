package com.motivbot.app.data.repository

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.AppResult
import com.motivbot.app.data.remote.OpenAiDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network-facing repository: the only thing the ViewModel knows about the AI server.
 *
 * The Swift ViewModel held `private let openAIService = OpenAIService()` — a hard-wired
 * dependency it could never substitute in a test. Behind this interface the data source is
 * injected, so a test can supply a fake without touching the network.
 */
interface MotivationRepository {
    /**
     * @param language the language the user is currently reading the app in; the AI is instructed
     *   to answer in it, so a Turkish user never gets an English quote back.
     */
    suspend fun generateMotivation(prompt: String, language: AppLanguage): AppResult<String>
}

@Singleton
class DefaultMotivationRepository @Inject constructor(
    private val remote: OpenAiDataSource,
) : MotivationRepository {

    override suspend fun generateMotivation(
        prompt: String,
        language: AppLanguage,
    ): AppResult<String> = remote.fetchMotivation(prompt, language)
}
