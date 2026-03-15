package com.rgbc.cloudBackup.di

import com.rgbc.cloudBackup.BuildConfig
import com.rgbc.cloudBackup.core.auth.AuthManager
import com.rgbc.cloudBackup.core.network.api.AuthApiService
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ── Production domain served via Cloudflare Tunnel ──────────────────
    // Points to your docker tunnel service: command: tunnel ... run --url http://app:3000
    // Trailing slash is REQUIRED by Retrofit.
    private const val PRODUCTION_URL = "https://api.bagariaa.in/"

    // ── Emulator localhost for debug builds ─────────────────────────────
    // 10.0.2.2 is the Android emulator alias for the host machine.
    private const val DEBUG_URL = "http://10.0.2.2:3000/"

    @Provides
    @Named("base_url")
    fun provideBaseUrl(): String {
        return if (BuildConfig.DEBUG) {
            Timber.d("🌐 Using DEBUG base URL: $DEBUG_URL")
            DEBUG_URL
        } else {
            Timber.d("🌐 Using PRODUCTION base URL: $PRODUCTION_URL")
            PRODUCTION_URL
        }
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            when {
                message.contains("Authorization", ignoreCase = true) -> {
                    Timber.d("Authorization: [REDACTED]")
                }
                message.contains("Bearer", ignoreCase = true) -> {
                    Timber.d("Bearer [REDACTED]")
                }
                else -> {
                    Timber.d(message)
                }
            }
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()

            val request = original.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)   // Increased for large file uploads over cellular
            .writeTimeout(120, TimeUnit.SECONDS)   // Increased for large file uploads over cellular
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        @Named("base_url") baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideBackupApiService(retrofit: Retrofit): BackupApiService {
        return retrofit.create(BackupApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService {
        return retrofit.create(AuthApiService::class.java)
    }
}