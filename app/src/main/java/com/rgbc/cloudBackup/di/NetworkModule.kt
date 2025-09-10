package com.rgbc.cloudBackup.di

import com.rgbc.cloudBackup.BuildConfig
import com.rgbc.cloudBackup.core.network.api.AuthApiService
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            // Filter out sensitive information
            when {
                message.contains("Authorization:", ignoreCase = true) -> {
                    Timber.d("Authorization: [HIDDEN]")
                }
                message.contains("Bearer ", ignoreCase = true) -> {
                    Timber.d("Bearer: [HIDDEN]")
                }
                message.contains("jwt", ignoreCase = true) -> {
                    Timber.d("JWT: [HIDDEN]")
                }
                message.contains("password", ignoreCase = true) -> {
                    Timber.d("Password: [HIDDEN]")
                }
                message.contains("token", ignoreCase = true) -> {
                    Timber.d("Token: [HIDDEN]")
                }
                else -> {
                    Timber.d(message)
                }
            }
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS // Only headers, not body
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // REMOVED: AuthInterceptor to break dependency cycle
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://rgbccloudserver-production.up.railway.app/")
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

// REMOVED: AuthInterceptor class entirely to break the cycle