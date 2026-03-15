package com.rgbc.cloudBackup.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rgbc.cloudBackup.BuildConfig
import com.rgbc.cloudBackup.core.network.api.AuthApiService
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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

    private const val PRODUCTION_URL = "https://api.bagariaa.in/"
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
                message.contains("Authorization", ignoreCase = true) ->
                    Timber.d("Authorization: [REDACTED]")
                message.contains("Bearer", ignoreCase = true) ->
                    Timber.d("Bearer [REDACTED]")
                else -> Timber.d(message)
            }
        }.apply {
            level = if (BuildConfig.DEBUG)
                HttpLoggingInterceptor.Level.BODY
            else
                HttpLoggingInterceptor.Level.NONE
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Auth interceptor — reads JWT from EncryptedSharedPreferences and
    // attaches it as a Bearer token to every request.
    //
    // Reads from prefs directly (not AuthManager) to avoid circular DI:
    // Interceptor → AuthManager → AuthApiService → Retrofit → OkHttpClient → Interceptor
    //
    // The "auth_prefs" name and MasterKey config match AuthManager exactly.
    // ════════════════════════════════════════════════════════════════════
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        @ApplicationContext context: Context
    ): Interceptor {
        val authPrefs by lazy {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "auth_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to open encrypted prefs in interceptor")
                context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
            }
        }

        return Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("Accept", "application/json")

            // Don't override Content-Type for multipart uploads
            if (original.body?.contentType()?.type != "multipart") {
                requestBuilder.header("Content-Type", "application/json")
            }

            // Attach Bearer token if available
            val token = authPrefs.getString("access_token", null)
            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
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