package com.motivbot.app.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.motivbot.app.BuildConfig
import com.motivbot.app.data.remote.AuthInterceptor
import com.motivbot.app.data.remote.OpenAiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    @Named("openAiApiKey")
    fun provideApiKey(): String = BuildConfig.OPENAI_API_KEY

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // The chat/completions payload carries far more fields than the DTOs model; without this
        // every response would fail to decode. Swift's JSONDecoder ignored unknown keys by default.
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            // Swift set `request.timeoutInterval = 60`, which bounds the whole request. OkHttp
            // splits that into phases, so each gets the same budget and `callTimeout` caps the total.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.OPENAI_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideOpenAiApi(retrofit: Retrofit): OpenAiApi = retrofit.create(OpenAiApi::class.java)
}
