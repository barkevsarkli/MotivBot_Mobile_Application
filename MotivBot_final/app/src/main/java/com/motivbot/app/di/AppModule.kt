package com.motivbot.app.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.motivbot.app.BuildConfig
import com.motivbot.app.core.AndroidStringProvider
import com.motivbot.app.core.StringProvider
import com.motivbot.app.data.remote.MotivBotApiService
import com.motivbot.app.data.remote.OkHttpTransport
import com.motivbot.app.data.remote.PredictionService
import com.motivbot.app.data.session.FileSessionStore
import com.motivbot.app.data.session.SessionStore
import com.motivbot.app.ui.CsvSink
import com.motivbot.app.ui.WellBeingDependencies
import com.motivbot.app.ui.export.ExportFiles
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideStringProvider(provider: AndroidStringProvider): StringProvider = provider

    @Provides
    @Singleton
    fun providePredictionService(@ApplicationContext context: Context): PredictionService {
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(true)
            // Results are sensitive; no HTTP cache is configured.
            .build()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return MotivBotApiService(
            baseUrl = BuildConfig.MOTIVBOT_API_BASE_URL.ifBlank { MotivBotApiService.DEFAULT_BASE_URL },
            transport = OkHttpTransport(client),
            isOnline = {
                val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            },
        )
    }

    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        FileSessionStore(File(context.noBackupFilesDir, "Assessment"))

    @Provides
    fun provideDependencies(
        service: PredictionService,
        store: SessionStore,
        strings: StringProvider,
        @ApplicationContext context: Context,
    ): WellBeingDependencies = WellBeingDependencies(
        service = service,
        store = store,
        strings = strings,
        csvSink = object : CsvSink {
            override fun write(csv: String): Boolean = ExportFiles.writeCsv(context, csv) != null
            override fun delete() = ExportFiles.deleteCsv(context)
        },
    )
}
