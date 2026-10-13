package com.motivbot.app.di

import android.content.Context
import androidx.room.Room
import com.motivbot.app.data.local.MotivBotDatabase
import com.motivbot.app.data.local.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MotivBotDatabase =
        Room.databaseBuilder(context, MotivBotDatabase::class.java, MotivBotDatabase.NAME)
            .build()

    @Provides
    fun provideSessionDao(database: MotivBotDatabase): SessionDao = database.sessionDao()
}
