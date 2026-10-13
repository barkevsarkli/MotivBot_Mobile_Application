package com.motivbot.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.motivbot.app.data.local.dao.SessionDao
import com.motivbot.app.data.local.entity.AnswerEntity
import com.motivbot.app.data.local.entity.SessionEntity

@Database(
    entities = [SessionEntity::class, AnswerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MotivBotDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        const val NAME = "motivbot.db"
    }
}
