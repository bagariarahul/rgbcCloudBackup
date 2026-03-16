package com.rgbc.cloudBackup.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MasterNodeResolver — Discovers the Master Node's tunnel URL via the API Gateway.
 *
 * FIXED (Sprint 2.4 hotfix):
 *   1. Logs EVERY decision point at Timber.w (visible in release logcat)
 *   2. Logs the raw Gateway response body for debugging
 *   3. Properly closes OkHttp response bodies to prevent connection leaks
 *   4. Uses the same SharedPreferences instance approach as the auth interceptor
 *   5. Handles Gson parsing failures gracefully
 */
@Singleton
class MasterNodeResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cachedUrl = AtomicReference<String?>(null)
    @Volatile private var cacheExpiresAt: Long = 0L
    private val cacheTtlMs = 60_000L

    private val gatewayUrl: String
        get() = if (com.rgbc.cloudBackup.BuildConfig.DEBUG) {
            "http://10.0.2.2:3000"
        } else {
            "https://api.bagariaa.in"
        }

    // ════════════════════════════════════════════════════════════════════
    // FIX 1: Build the plain client EXACTLY the same way NetworkModule's
    // auth interceptor reads the token — same prefs name, same MasterKey.
    // The lazy block logs success/failure so we know if prefs init worked.
    // ════════════════════════════════════════════════════════════════════
    private val authPrefs: SharedPreferences by lazy {
        try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                context, "auth_prefs", masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Timber.i("P2P MasterResolver: EncryptedSharedPreferences opened successfully")
            prefs
        } catch (e: Exception) {
            Timber.e(e, "P2P MasterResolver: EncryptedSharedPreferences FAILED, using fallback")
            context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val plainClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("Accept", "application/json")

                val token = authPrefs.getString("access_token", null)
                if (!token.isNullOrEmpty()) {
                    builder.header("Authorization", "Bearer $token")
                    Timber.d("P2P MasterResolver: Bearer token attached (${token.length} chars)")
                } else {
                    Timber.w("P2P MasterResolver: NO Bearer token available — Gateway will return 401!")
                }

                chain.proceed(builder.build())
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val gson = Gson()

    /**
     * Get the Master's tunnel URL. Returns null if Master is offline or unreachable.
     * Thread-safe. Called from OkHttp interceptor thread.
     */
    fun getMasterUrl(): String? {
        val now = System.currentTimeMillis()

        // Check cache first
        if (now < cacheExpiresAt) {
            val cached = cachedUrl.get()
            Timber.d("P2P MasterResolver: cache hit → ${cached ?: "null (Master offline)"}")
            return cached
        }

        Timber.i("P2P MasterResolver: cache expired, querying Gateway...")
        return refreshFromGateway()
    }

    fun invalidateCache() {
        cacheExpiresAt = 0L
        cachedUrl.set(null)
        Timber.w("P2P MasterResolver: cache INVALIDATED")
    }

    // ════════════════════════════════════════════════════════════════════
    // FIX 2: Full diagnostic logging on every possible failure path.
    // FIX 3: response.body is consumed inside a use{} block to prevent leaks.
    // ════════════════════════════════════════════════════════════════════
    private fun refreshFromGateway(): String? {
        val endpoint = "$gatewayUrl/api/devices/master"
        Timber.i("P2P MasterResolver: GET $endpoint")

        try {
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .build()

            val response = plainClient.newCall(request).execute()

            return response.use { resp ->
                val code = resp.code
                val rawBody = resp.body?.string()

                if (!resp.isSuccessful) {
                    // ── FAILURE PATH A: Gateway returned an error ────
                    Timber.e(
                        "P2P MasterResolver: Gateway returned HTTP $code\n" +
                                "   Body: ${rawBody?.take(300) ?: "empty"}\n" +
                                "   This usually means:\n" +
                                "     401 → Bearer token missing/expired (check login)\n" +
                                "     404 → Admin user not found (login via Google OAuth first)\n" +
                                "     500 → Server error"
                    )
                    return@use updateCache(null)
                }

                if (rawBody.isNullOrEmpty()) {
                    Timber.e("P2P MasterResolver: Gateway returned 200 but EMPTY body")
                    return@use updateCache(null)
                }

                Timber.d("P2P MasterResolver: raw response → ${rawBody.take(500)}")

                // ── Parse the JSON ──────────────────────────────────
                val masterResponse = try {
                    gson.fromJson(rawBody, MasterDiscoveryResponse::class.java)
                } catch (e: Exception) {
                    Timber.e(e, "P2P MasterResolver: Gson parsing FAILED on body: ${rawBody.take(200)}")
                    return@use updateCache(null)
                }

                if (masterResponse == null) {
                    Timber.e("P2P MasterResolver: Gson returned null object")
                    return@use updateCache(null)
                }

                Timber.i("P2P MasterResolver: status=${masterResponse.status}, master=${masterResponse.master != null}")

                if (masterResponse.status == "MASTER_ONLINE" && masterResponse.master != null) {
                    val tunnelUrl = masterResponse.master.tunnelUrl

                    if (!tunnelUrl.isNullOrBlank()) {
                        val cleanUrl = tunnelUrl.trimEnd('/')
                        Timber.i("P2P MasterResolver: ✅ MASTER ONLINE → $cleanUrl " +
                                "(device: ${masterResponse.master.deviceName})")
                        return@use updateCache(cleanUrl)
                    } else {
                        // ── FAILURE PATH B: Master is online but tunnel_url is null ──
                        Timber.w(
                            "P2P MasterResolver: Master is ONLINE but tunnel_url is NULL!\n" +
                                    "   device_name: ${masterResponse.master.deviceName}\n" +
                                    "   local_ip: ${masterResponse.master.localIp}:${masterResponse.master.localPort}\n" +
                                    "   This means cloudflared isn't running on the Master,\n" +
                                    "   or the Master's TUNNEL_URL/.env isn't configured."
                        )
                        // Try LAN fallback: use local_ip if available
                        val localIp = masterResponse.master.localIp
                        val localPort = masterResponse.master.localPort
                        if (!localIp.isNullOrBlank() && localPort != null && localPort > 0) {
                            val lanUrl = "http://$localIp:$localPort"
                            Timber.w("P2P MasterResolver: Falling back to LAN → $lanUrl")
                            return@use updateCache(lanUrl)
                        }
                        return@use updateCache(null)
                    }
                } else {
                    Timber.w(
                        "P2P MasterResolver: Master not available. " +
                                "status=${masterResponse.status}, " +
                                "message=${masterResponse.message ?: "none"}"
                    )
                    return@use updateCache(null)
                }
            }

        } catch (e: java.net.ConnectException) {
            Timber.e("P2P MasterResolver: Gateway UNREACHABLE (ConnectException: ${e.message})")
            return gracefulStaleReturn()
        } catch (e: java.net.SocketTimeoutException) {
            Timber.e("P2P MasterResolver: Gateway TIMEOUT (${e.message})")
            return gracefulStaleReturn()
        } catch (e: Exception) {
            Timber.e(e, "P2P MasterResolver: UNEXPECTED error during discovery")
            return gracefulStaleReturn()
        }
    }

    private fun gracefulStaleReturn(): String? {
        val stale = cachedUrl.get()
        if (stale != null) {
            cacheExpiresAt = System.currentTimeMillis() + 10_000L
            Timber.w("P2P MasterResolver: Using STALE cache for 10s → $stale")
        }
        return stale
    }

    private fun updateCache(url: String?): String? {
        cachedUrl.set(url)
        cacheExpiresAt = System.currentTimeMillis() + cacheTtlMs
        return url
    }
}

// ── Response data classes ────────────────────────────────────────────────
// ALL fields are nullable to prevent Gson from throwing on unexpected shapes

data class MasterDiscoveryResponse(
    val master: MasterInfo? = null,
    val status: String? = null,
    val message: String? = null
)

data class MasterInfo(
    @SerializedName("device_id")
    val deviceId: String? = null,
    @SerializedName("device_name")
    val deviceName: String? = null,
    @SerializedName("device_type")
    val deviceType: String? = null,
    val role: String? = null,
    @SerializedName("tunnel_url")
    val tunnelUrl: String? = null,
    @SerializedName("local_ip")
    val localIp: String? = null,
    @SerializedName("local_port")
    val localPort: Int? = null,
    @SerializedName("file_count")
    val fileCount: Int? = null,
    @SerializedName("disk_free_bytes")
    val diskFreeBytes: Long? = null,
    @SerializedName("os_platform")
    val osPlatform: String? = null,
    @SerializedName("last_seen_at")
    val lastSeenAt: String? = null,
    @SerializedName("is_online")
    val isOnline: Boolean? = null
)