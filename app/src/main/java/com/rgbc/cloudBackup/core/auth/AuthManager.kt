package com.rgbc.cloudBackup.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.rgbc.cloudBackup.core.network.api.*

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Lazy inject to avoid circular dependency
    @Inject
    lateinit var authApiService: AuthApiService

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
            Timber.e(e, "Failed to create encrypted preferences, using fallback")
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
        Timber.d("🔐 AuthManager initialized")
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        try {
            val token = getAccessToken()
            if (!token.isNullOrEmpty()) {
                val userData = getCurrentUserFromPrefs()
                _authState.value = AuthState.Authenticated(userData)
                Timber.d("🔐 Existing authentication found")
            } else {
                _authState.value = AuthState.Unauthenticated
                Timber.d("🔐 No existing authentication")
            }
        } catch (e: Exception) {
            Timber.e(e, "🔐 Error checking existing auth")
            _authState.value = AuthState.Unauthenticated
        }
    }

    // REMOVED: testConnection() method to fix compilation error

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String
    ): AuthResult {
        return try {
            Timber.d("🔐 Starting registration for $email")
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

            val response = authApiService.register(request)

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                saveAuthData(authResponse)
                _authState.value = AuthState.Authenticated(authResponse.user)
                Timber.d("🔐 Registration successful!")
                AuthResult.Success(authResponse.user)
            } else {
                val error = "Registration failed (${response.code()})"
                Timber.w("🔐 Registration failed: $error")
                _authState.value = AuthState.Error(error)
                AuthResult.Error(error)
            }
        } catch (e: Exception) {
            val error = "Registration failed: ${e.message}"
            Timber.e(e, "🔐 Registration exception")
            _authState.value = AuthState.Error(error)
            AuthResult.Error(error)
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            Timber.d("🔐 Starting login for $email")
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
                saveAuthData(authResponse)
                _authState.value = AuthState.Authenticated(authResponse.user)
                Timber.d("🔐 Login successful!")
                AuthResult.Success(authResponse.user)
            } else {
                val error = "Login failed (${response.code()})"
                Timber.w("🔐 Login failed: $error")
                _authState.value = AuthState.Error(error)
                AuthResult.Error(error)
            }
        } catch (e: Exception) {
            val error = "Login failed: ${e.message}"
            Timber.e(e, "🔐 Login exception")
            _authState.value = AuthState.Error(error)
            AuthResult.Error(error)
        }
    }

    suspend fun logout() {
        try {
            authApiService.logout(LogoutRequest(null))
        } catch (e: Exception) {
            Timber.w(e, "🔐 Logout API call failed")
        } finally {
            clearAuthData()
            _authState.value = AuthState.Unauthenticated
            Timber.d("🔐 Logout complete")
        }
    }

    fun getAccessToken(): String? {
        return encryptedPrefs.getString("access_token", null)
    }

    private fun saveAuthData(authResponse: AuthResponse) {
        encryptedPrefs.edit().apply {
            putString("access_token", authResponse.tokens.accessToken)
            putString("refresh_token", authResponse.tokens.refreshToken)
            putString("user_id", authResponse.user.id)
            putString("user_email", authResponse.user.email)
            putString("user_firstname", authResponse.user.firstName)
            putString("user_lastname", authResponse.user.lastName)
            apply()
        }
        Timber.d("🔐 Auth data saved")
    }

    private fun getCurrentUserFromPrefs(): UserData {
        return UserData(
            id = encryptedPrefs.getString("user_id", "") ?: "",
            email = encryptedPrefs.getString("user_email", "") ?: "",
            firstName = encryptedPrefs.getString("user_firstname", "") ?: "",
            lastName = encryptedPrefs.getString("user_lastname", "") ?: "",
            storageQuota = "107374182400", // FIXED: Provide default value
            storageUsed = "0",             // FIXED: Provide default value
            createdAt = ""                 // FIXED: Provide default value
        )
    }

    private fun clearAuthData() {
        encryptedPrefs.edit().clear().apply()
        Timber.d("🔐 Auth data cleared")
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