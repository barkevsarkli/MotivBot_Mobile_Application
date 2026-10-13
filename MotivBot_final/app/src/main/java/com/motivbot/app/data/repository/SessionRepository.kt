package com.motivbot.app.data.repository

import androidx.room.withTransaction
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.data.local.MotivBotDatabase
import com.motivbot.app.data.local.entity.AnswerEntity
import com.motivbot.app.data.local.entity.SessionEntity
import com.motivbot.app.domain.usecase.AnswerRow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-storage repository — the layer the Swift app simply did not have.
 *
 * Everything here is `suspend`/`Flow`; Room enforces main-safety for both, so no call site needs
 * its own dispatcher juggling.
 */
interface SessionRepository {
    suspend fun startSession(
        emotionText: String,
        language: AppLanguage,
        rows: List<AnswerRow>,
    ): Long

    suspend fun saveAiResponse(sessionId: Long, response: String, usedFallback: Boolean)

    suspend fun completeSession(sessionId: Long, reflection: String?, feelingIndex: Int?)

    fun observeSessions(): Flow<List<SessionEntity>>

    suspend fun deleteSession(sessionId: Long)
}

@Singleton
class DefaultSessionRepository @Inject constructor(
    private val database: MotivBotDatabase,
) : SessionRepository {

    private val dao = database.sessionDao()

    override suspend fun startSession(
        emotionText: String,
        language: AppLanguage,
        rows: List<AnswerRow>,
    ): Long = database.withTransaction {
        // Session row and answer rows must land together — a session with half its answers is
        // worse than no session at all for a longitudinal well-being record.
        val sessionId = dao.insertSession(
            SessionEntity(
                createdAt = System.currentTimeMillis(),
                languageTag = language.tag,
                emotionText = emotionText,
            ),
        )

        dao.insertAnswers(
            rows.map { row ->
                AnswerEntity(
                    sessionId = sessionId,
                    sectionId = row.sectionId,
                    questionId = row.questionId,
                    sectionTitle = row.sectionTitle,
                    questionText = row.questionText,
                    answerText = row.answerText,
                    score = row.score,
                )
            },
        )

        sessionId
    }

    override suspend fun saveAiResponse(sessionId: Long, response: String, usedFallback: Boolean) {
        dao.updateAiResponse(sessionId, response, usedFallback)
    }

    override suspend fun completeSession(sessionId: Long, reflection: String?, feelingIndex: Int?) {
        dao.completeSession(
            sessionId = sessionId,
            reflection = reflection?.takeIf { it.isNotBlank() },
            feelingIndex = feelingIndex,
            completedAt = System.currentTimeMillis(),
        )
    }

    override fun observeSessions(): Flow<List<SessionEntity>> = dao.observeSessions()

    override suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)
}
