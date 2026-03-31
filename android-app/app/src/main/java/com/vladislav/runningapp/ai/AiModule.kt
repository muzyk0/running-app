package com.vladislav.runningapp.ai

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.vladislav.runningapp.BuildConfig
import com.vladislav.runningapp.ai.data.remote.DefaultTrainingGenerationRepository
import com.vladislav.runningapp.ai.data.remote.TrainingGenerationApiService
import com.vladislav.runningapp.ai.domain.TrainingGenerationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TrainingApiBaseUrl

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    abstract fun bindTrainingGenerationRepository(
        defaultTrainingGenerationRepository: DefaultTrainingGenerationRepository,
    ): TrainingGenerationRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AiNetworkModule {
    private const val TrainingApiConnectTimeoutSec = 15L
    private const val TrainingApiReadTimeoutSec = 11 * 60L
    private const val TrainingApiWriteTimeoutSec = 30L
    private const val TrainingApiCallTimeoutSec = 11 * 60L

    @Provides
    @TrainingApiBaseUrl
    fun provideTrainingApiBaseUrl(): String {
        val normalizedBaseUrl = BuildConfig.TRAINING_API_BASE_URL
            .trim()
            .ifEmpty { "http://10.0.2.2:8080/" }
            .let { value ->
                if (value.endsWith("/")) value else "$value/"
            }

        check(BuildConfig.DEBUG || !normalizedBaseUrl.startsWith("http://")) {
            "Release builds require an HTTPS training API base URL."
        }

        return normalizedBaseUrl
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TrainingApiConnectTimeoutSec, TimeUnit.SECONDS)
        .readTimeout(TrainingApiReadTimeoutSec, TimeUnit.SECONDS)
        .writeTimeout(TrainingApiWriteTimeoutSec, TimeUnit.SECONDS)
        .callTimeout(TrainingApiCallTimeoutSec, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @TrainingApiBaseUrl baseUrl: String,
        okHttpClient: OkHttpClient,
        gson: Gson,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    @Provides
    @Singleton
    fun provideTrainingGenerationApiService(
        retrofit: Retrofit,
    ): TrainingGenerationApiService = retrofit.create(TrainingGenerationApiService::class.java)
}
