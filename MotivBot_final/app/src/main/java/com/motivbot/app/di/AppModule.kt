package com.motivbot.app.di

import com.motivbot.app.core.AndroidStringProvider
import com.motivbot.app.core.StringProvider
import com.motivbot.app.data.repository.DefaultMotivationRepository
import com.motivbot.app.data.repository.DefaultSessionRepository
import com.motivbot.app.data.repository.DefaultSettingsRepository
import com.motivbot.app.data.repository.MotivationRepository
import com.motivbot.app.data.repository.SessionRepository
import com.motivbot.app.data.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMotivationRepository(impl: DefaultMotivationRepository): MotivationRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: DefaultSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindStringProvider(impl: AndroidStringProvider): StringProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    /**
     * Injecting the dispatcher rather than hard-coding `Dispatchers.IO` lets tests swap in a
     * `StandardTestDispatcher` and control virtual time.
     */
    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
