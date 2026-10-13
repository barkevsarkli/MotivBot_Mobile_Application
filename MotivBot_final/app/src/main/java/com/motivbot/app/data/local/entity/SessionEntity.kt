package com.motivbot.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completed (or in-flight) assessment run.
 *
 * The Swift app had no persistence at all — the answers lived in `@Observable` properties and were
 * flushed to a throwaway CSV in `FileManager.temporaryDirectory`, so closing the app lost
 * everything. This table is the durable equivalent; the CSV export now reads from it.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val languageTag: String,
    val emotionText: String,
    val aiResponse: String? = null,
    /** True when the AI call failed and the local fallback quote was shown instead. */
    val usedFallbackResponse: Boolean = false,
    val reflectionText: String? = null,
    /** Index into `feelingEmojis`; null until the user picks one. */
    val feelingIndex: Int? = null,
    val completedAt: Long? = null,
)

/**
 * One answered question, denormalised on purpose.
 *
 * [questionText] and [answerText] store the *resolved* strings rather than resource ids: resource
 * ids are not stable across builds, and a session exported months later must still read back the
 * wording the user actually saw, in the language they saw it in.
 */
@Entity(
    tableName = "answers",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index(value = ["sessionId", "questionId"], unique = true)],
)
data class AnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sectionId: Int,
    val questionId: Int,
    val sectionTitle: String,
    val questionText: String,
    val answerText: String,
    /** Null for free-text answers — the CSV writes "N/A" for those, as the Swift export did. */
    val score: Int? = null,
)
