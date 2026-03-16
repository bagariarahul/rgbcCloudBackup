package com.rgbc.cloudBackup.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rgbc.cloudBackup.BuildConfig
import com.rgbc.cloudBackup.core.network.MasterNodeResolver
import com.rgbc.cloudBackup.core.network.api.AuthApiService
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    // Paths that should be redirected to the Master Node when online.
    private val MASTER_REDIRECT_PREFIXES = listOf(
        "/api/files/",
        "/api/server-info"
    )

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
            // ── Filter rules ────────────────────────────────────────
            // 1. Redact auth headers (existing behavior)
            // 2. Skip binary body dumps (lines > 1KB are almost certainly
            //    raw file bytes from multipart uploads)
            // 3. Replace OkHttp's raw body dump with a summary line
            // 4. Keep everything else (URLs, status codes, JSON bodies)
            when {
                // Redact sensitive headers
                message.contains("Authorization", ignoreCase = true) ->
                    Timber.d("Authorization: [REDACTED]")
                message.contains("Bearer", ignoreCase = true) ->
                    Timber.d("Bearer [REDACTED]")

                // Skip binary body dumps — these are the multipart file bytes
                // that flood logcat with thousands of gibberish lines.
                // JSON responses are rarely > 1KB per line.
                message.length > 1024 ->
                    Timber.d("[BINARY BODY: ${message.length} chars omitted]")

                // Skip OkHttp's raw byte-dump lines that start with non-printable chars
                // (multipart boundaries followed by binary data)
                message.any { it < ' ' && it != '\t' && it != '\n' && it != '\r' } ->
                    Timber.d("[BINARY LINE omitted]")

                // Everything else: log normally (URLs, headers, JSON, status codes)
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
    // Auth interceptor — attaches Bearer token to every request.
    // ════════════════════════════════════════════════════════════════════
    @Provides
    @Singleton
    @Named("auth_interceptor")
    fun provideAuthInterceptor(
        @ApplicationContext context: Context
    ): Interceptor {
        val authPrefs by lazy {
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context, "auth_prefs", masterKey,
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

            if (original.body?.contentType()?.type != "multipart") {
                requestBuilder.header("Content-Type", "application/json")
            }

            val token = authPrefs.getString("access_token", null)
            if (!token.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            chain.proceed(requestBuilder.build())
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Sprint 2.4: URL-rewriting interceptor
    //
    // FIXED: Full diagnostic logging at every decision point.
    // ════════════════════════════════════════════════════════════════════
    @Provides
    @Singleton
    @Named("master_redirect_interceptor")
    fun provideMasterRedirectInterceptor(
        masterNodeResolver: MasterNodeResolver
    ): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()
            val originalUrl = original.url
            val path = originalUrl.encodedPath

            // ── Step 1: Check if this path should be redirected ──────
            val shouldRedirect = MASTER_REDIRECT_PREFIXES.any { prefix ->
                path.startsWith(prefix)
            }

            if (!shouldRedirect) {
                Timber.d("P2P Interceptor: SKIP (path=$path, not a file operation)")
                return@Interceptor chain.proceed(original)
            }

            Timber.i("P2P Interceptor: path=$path → eligible for Master redirect")

            // ── Step 2: Resolve the Master URL ──────────────────────
            val masterUrl: String? = try {
                masterNodeResolver.getMasterUrl()
            } catch (e: Exception) {
                Timber.e(e, "P2P Interceptor: getMasterUrl() threw an exception")
                null
            }

            if (masterUrl.isNullOrBlank()) {
                Timber.w("P2P Interceptor: Master URL is NULL → using Gateway for $path")
                return@Interceptor chain.proceed(original)
            }

            Timber.i("P2P Interceptor: Master URL resolved → $masterUrl")

            // ── Step 3: Parse and rewrite the URL ───────────────────
            val masterBaseUrl = masterUrl.toHttpUrlOrNull()
            if (masterBaseUrl == null) {
                Timber.e("P2P Interceptor: INVALID Master URL '$masterUrl' → using Gateway")
                masterNodeResolver.invalidateCache()
                return@Interceptor chain.proceed(original)
            }

            val newUrl = originalUrl.newBuilder()
                .scheme(masterBaseUrl.scheme)
                .host(masterBaseUrl.host)
                .port(masterBaseUrl.port)
                .build()

            val redirectedRequest = original.newBuilder()
                .url(newUrl)
                .build()

            Timber.i(
                "P2P Interceptor: REDIRECTING\n" +
                        "   FROM: ${originalUrl.host}${path}\n" +
                        "   TO:   ${newUrl.host}:${newUrl.port}${path}"
            )

            // ── Step 4: Execute against Master, fallback to Gateway ─
            try {
                val response = chain.proceed(redirectedRequest)

                // If Master returns 401/403, the Slave's token is likely
                // not accepted by the Master. Log it loudly.
                if (response.code == 401 || response.code == 403) {
                    Timber.e(
                        "P2P Interceptor: Master REJECTED with ${response.code}!\n" +
                                "   This means the Master's JWT_SECRET or M2M_API_KEY\n" +
                                "   doesn't match. Check the Master's .env file."
                    )
                }

                Timber.i("P2P Interceptor: Master responded with HTTP ${response.code}")
                response
            } catch (e: java.net.ConnectException) {
                Timber.e("P2P Interceptor: Master UNREACHABLE (ConnectException: ${e.message})")
                Timber.w("P2P Interceptor: Falling back to Gateway for $path")
                masterNodeResolver.invalidateCache()
                chain.proceed(original)
            } catch (e: java.net.SocketTimeoutException) {
                Timber.e("P2P Interceptor: Master TIMEOUT (${e.message})")
                Timber.w("P2P Interceptor: Falling back to Gateway for $path")
                masterNodeResolver.invalidateCache()
                chain.proceed(original)
            } catch (e: javax.net.ssl.SSLException) {
                Timber.e("P2P Interceptor: Master SSL ERROR (${e.message})")
                Timber.w("P2P Interceptor: Falling back to Gateway for $path")
                masterNodeResolver.invalidateCache()
                chain.proceed(original)
            } catch (e: Exception) {
                Timber.e(e, "P2P Interceptor: UNEXPECTED error connecting to Master")
                Timber.w("P2P Interceptor: Falling back to Gateway for $path")
                masterNodeResolver.invalidateCache()
                chain.proceed(original)
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        @Named("auth_interceptor") authInterceptor: Interceptor,
        @Named("master_redirect_interceptor") masterRedirectInterceptor: Interceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)            // 1st: attach Bearer token
            .addInterceptor(masterRedirectInterceptor)  // 2nd: rewrite URL to Master
            .addInterceptor(loggingInterceptor)         // 3rd: log final request
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