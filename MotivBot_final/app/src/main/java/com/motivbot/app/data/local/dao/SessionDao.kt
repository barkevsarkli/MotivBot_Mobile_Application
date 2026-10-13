package com.motivbot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.motivbot.app.data.local.entity.AnswerEntity
import com.motivbot.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * All reads are `Flow` (reactive, like the Swift `@Observable` properties the views bound to);
 * all writes are `suspend` (main-safe by construction — Room dispatches them off the main thread).
 */
@Dao
interface SessionDao {

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswers(answers: List<AnswerEntity>)

    @Query("UPDATE sessions SET aiResponse = :response, usedFallbackResponse = :usedFallback WHERE id = :sessionId")
    suspend fun updateAiResponse(sessionId: Long, response: String, usedFallback: Boolean)

    @Query(
        """
        UPDATE sessions
        SET reflectionText = :reflection, feelingIndex = :feelingIndex, completedAt = :completedAt
        WHERE id = :sessionId
        """,
    )
    suspend fun completeSession(
        sessionId: Long,
        reflection: String?,
        feelingIndex: Int?,
        completedAt: Long,
    )

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: Long): SessionEntity?

    @Query("SELECT * FROM answers WHERE sessionId = :sessionId ORDER BY sectionId, questionId")
    suspend fun getAnswers(sessionId: Long): List<AnswerEntity>

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun observeSessions(): Flow<List<SessionEntity>>

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
