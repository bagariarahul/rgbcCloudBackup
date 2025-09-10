package com.rgbc.cloudBackup.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rgbc.cloudBackup.core.network.api.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authApiService: AuthApiService
) {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val encryptedPrefs by lazy {
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
            Timber.e(e, "Failed to create encrypted preferences")
            context.getSharedPreferences("auth_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val deviceId: String by lazy {
        encryptedPrefs.getString("device_id", null) ?: run {
            val newDeviceId = generateDeviceId()
            encryptedPrefs.edit().putString("device_id", newDeviceId).apply()
            newDeviceId
        }
    }

    private val deviceName: String by lazy {
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    }

    init {
        Timber.d("🔧 AuthManager initialized")
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        try {
            Timber.d("🔍 Checking existing authentication...")
            val token = getAccessToken()

            if (!token.isNullOrEmpty()) {
                Timber.d("✅ Found existing token, user is authenticated")
                val userData = getCurrentUserFromPrefs()
                _authState.value = AuthState.Authenticated(userData)
            } else {
                Timber.d("❌ No token found, user needs to authenticate")
                _authState.value = AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking existing auth")
            _authState.value = AuthState.Unauthenticated
        }
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): AuthResult {
        return try {
            Timber.d("📝 Starting registration for: $email")
            _authState.value = AuthState.Loading

            val request = RegisterRequest(
                email = email,
                password = password,
                firstName = firstName,
                lastName = lastName,
                deviceName = deviceName,
                deviceType = "ANDROID",
                deviceId = deviceId
            )

            Timber.d("📡 Sending registration request...")
            val response = authApiService.register(request)

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                Timber.d("✅ Registration successful!")

                saveAuthData(authResponse)
                _authState.value = AuthState.Authenticated(authResponse.user)
                AuthResult.Success(authResponse.user)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Timber.w("❌ Registration failed: $errorBody")
                val error = "Registration failed: ${response.code()}"
                _authState.value = AuthState.Error(error)
                AuthResult.Error(error)
            }

        } catch (e: Exception) {
            val error = "Registration failed: ${e.message}"
            Timber.e(e, "💥 Registration exception")
            _authState.value = AuthState.Error(error)
            AuthResult.Error(error)
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            Timber.d("🔑 Starting login for: $email")
            _authState.value = AuthState.Loading

            val request = LoginRequest(
                email = email,
                password = password,
                deviceName = deviceName,
                deviceType = "ANDROID",
                deviceId = deviceId
            )

            val response = authApiService.login(request)

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                Timber.d("✅ Login successful!")

                saveAuthData(authResponse)
                _authState.value = AuthState.Authenticated(authResponse.user)
                AuthResult.Success(authResponse.user)
            } else {
                val error = "Login failed: ${response.code()}"
                Timber.w("❌ Login failed")
                _authState.value = AuthState.Error(error)
                AuthResult.Error(error)
            }

        } catch (e: Exception) {
            val error = "Login failed: ${e.message}"
            Timber.e(e, "💥 Login exception")
            _authState.value = AuthState.Error(error)
            AuthResult.Error(error)
        }
    }

    suspend fun logout() {
        try {
            Timber.d("👋 Logging out...")
            val sessionId = getSessionId()
            if (!sessionId.isNullOrEmpty()) {
                authApiService.logout(LogoutRequest(sessionId))
            }
        } catch (e: Exception) {
            Timber.w(e, "Logout API call failed")
        } finally {
            clearAuthData()
            _authState.value = AuthState.Unauthenticated
            Timber.d("✅ Logout complete")
        }
    }

    fun getAccessToken(): String? {
        return encryptedPrefs.getString("access_token", null)
    }

    private fun saveAuthData(authResponse: AuthResponse) {
        encryptedPrefs.edit().apply {
            putString("access_token", authResponse.tokens.accessToken)
            putString("refresh_token", authResponse.tokens.refreshToken)
            putString("session_id", authResponse.sessionId)
            putString("user_id", authResponse.user.id)
            putString("user_email", authResponse.user.email)
            putString("user_first_name", authResponse.user.firstName)
            putString("user_last_name", authResponse.user.lastName)
            apply()
        }
        Timber.d("💾 Auth data saved")
    }

    private fun getCurrentUserFromPrefs(): UserData {
        return UserData(
            id = encryptedPrefs.getString("user_id", "") ?: "",
            email = encryptedPrefs.getString("user_email", "") ?: "",
            firstName = encryptedPrefs.getString("user_first_name", "") ?: "",
            lastName = encryptedPrefs.getString("user_last_name", "") ?: "",
            storageQuota = "107374182400",
            storageUsed = "0",
            createdAt = ""
        )
    }

    private fun getSessionId(): String? {
        return encryptedPrefs.getString("session_id", null)
    }

    private fun clearAuthData() {
        encryptedPrefs.edit().clear().apply()
        Timber.d("🗑️ Auth data cleared")
    }

    private fun generateDeviceId(): String {
        return "${android.os.Build.BRAND}_${android.os.Build.MODEL}_${System.currentTimeMillis()}"
            .replace(" ", "_")
            .replace("-", "_")
    }
}

sealed class AuthState {
    object Loading : AuthState()
    object Unauthenticated : AuthState()
    data class Authenticated(val user: UserData) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class AuthResult {
    data class Success(val user: UserData) : AuthResult()
    data class Error(val message: String) : AuthResult()
}