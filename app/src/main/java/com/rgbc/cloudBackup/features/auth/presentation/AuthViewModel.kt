package com.rgbc.cloudBackup.features.auth.presentation

import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rgbc.cloudBackup.BuildConfig
import com.rgbc.cloudBackup.core.auth.AuthManager
import com.rgbc.cloudBackup.core.auth.AuthResult
import com.rgbc.cloudBackup.core.auth.AuthState
import com.rgbc.cloudBackup.core.data.database.entity.BackupQueueDao
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import com.rgbc.cloudBackup.core.preferences.SettingsManager
import com.rgbc.cloudBackup.core.service.BackupScheduler
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val fileRepository: FileRepository,
    private val backupDirectoryDao: BackupDirectoryDao,
    private val backupQueueDao: BackupQueueDao,
    private val settingsManager: SettingsManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val authState: StateFlow<AuthState> = authManager.authState

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val credentialManager by lazy { CredentialManager.create(appContext) }

    // ════════════════════════════════════════════════════════════════════
    // ISSUE 1 FIX: Full logout wipe
    //
    // Clears: EncryptedSharedPreferences (tokens) + Room (all 3 tables)
    //         + DataStore (settings) + WorkManager (scheduled backups)
    // ════════════════════════════════════════════════════════════════════
    fun logout() {
        viewModelScope.launch {
            Timber.i("🚪 Full logout initiated — wiping all local data")

            // 1. API logout + clear auth tokens (EncryptedSharedPreferences)
            authManager.logout()

            // 2. Wipe all Room tables
            try {
                fileRepository.clearAllFiles()
                Timber.d("🧹 file_index table cleared")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear file_index")
            }

            try {
                backupQueueDao.clearAll()
                Timber.d("🧹 backup_queue table cleared")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear backup_queue")
            }

            try {
                backupDirectoryDao.clearAll()
                Timber.d("🧹 backup_directories table cleared")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear backup_directories")
            }

            // 3. Wipe DataStore preferences
            try {
                settingsManager.clearAll()
                Timber.d("🧹 DataStore preferences cleared")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear DataStore")
            }

            // 4. Cancel scheduled WorkManager backups
            try {
                BackupScheduler.cancelAll(appContext)
                Timber.d("🧹 WorkManager backup cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel WorkManager")
            }

            Timber.i("🚪 Full logout complete — all local data wiped")
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ISSUE 2 FIX: Dual-flow Google Sign-In
    //
    // Flow 1 (modern): CredentialManager API — works on Pixel/Samsung
    //                   with up-to-date Play Services
    // Flow 2 (legacy):  GoogleSignInClient intent — works on emulators
    //                   and older devices
    //
    // The ViewModel tries Flow 1 first. On any GetCredentialException,
    // it calls onCredentialManagerFailed() which the Composable uses
    // to launch the legacy GoogleSignIn intent.
    // ════════════════════════════════════════════════════════════════════
    fun signInWithGoogle(
        activityContext: Context,
        onCredentialManagerFailed: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGoogleLoading = true)
            Timber.d("🔐 Attempting Google Sign-In via Credential Manager")

            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = activityContext,
                    request = request
                )

                // Extract ID token from Credential Manager response
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    Timber.d("🔐 Credential Manager succeeded, sending token to backend")
                    sendIdTokenToBackend(googleCredential.idToken)
                } else {
                    Timber.w("🔐 Unexpected credential type: ${credential.javaClass.simpleName}")
                    _uiState.value = _uiState.value.copy(isGoogleLoading = false)
                }

            } catch (e: GetCredentialException) {
                // ── Credential Manager failed — fall back to legacy flow ─
                Timber.w("🔐 Credential Manager failed (${e.javaClass.simpleName}): ${e.message}")
                Timber.d("🔐 Falling back to legacy GoogleSignInClient")
                onCredentialManagerFailed()
                // Don't set isGoogleLoading = false here — the legacy flow is still in progress
            } catch (e: Exception) {
                Timber.e(e, "🔐 Unexpected Google Sign-In error")
                _uiState.value = _uiState.value.copy(isGoogleLoading = false)
            }
        }
    }

    /**
     * Called by AuthScreen when the legacy GoogleSignInClient intent returns.
     * Extracts the ID token from the result Intent and sends it to the backend.
     */
    fun handleLegacyGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            try {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn
                    .getSignedInAccountFromIntent(data)
                val account = task.getResult(
                    com.google.android.gms.common.api.ApiException::class.java
                )

                val idToken = account?.idToken
                if (idToken != null) {
                    Timber.d("🔐 Legacy GoogleSignIn succeeded, sending token to backend")
                    sendIdTokenToBackend(idToken)
                } else {
                    Timber.e("🔐 Legacy GoogleSignIn returned null ID token")
                    _uiState.value = _uiState.value.copy(isGoogleLoading = false)
                }
            } catch (e: Exception) {
                Timber.e(e, "🔐 Legacy GoogleSignIn result processing failed")
                _uiState.value = _uiState.value.copy(isGoogleLoading = false)
            }
        }
    }

    /**
     * Common path: send the Google ID token to our backend for JWT exchange.
     * Called by both Credential Manager and legacy flow.
     */
    private suspend fun sendIdTokenToBackend(idToken: String) {
        val result = authManager.loginWithGoogle(idToken)
        when (result) {
            is AuthResult.Success -> {
                Timber.i("🔐 Backend auth complete: ${result.user.email}")
            }
            is AuthResult.Error -> {
                Timber.e("🔐 Backend rejected token: ${result.message}")
            }
        }
        _uiState.value = _uiState.value.copy(isGoogleLoading = false)
    }

    // ── Existing email/password auth (unchanged) ────────────────────

    fun setLoginMode(isLogin: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLogin = isLogin, email = "", password = "",
            firstName = "", lastName = ""
        )
    }

    fun updateEmail(email: String) { _uiState.value = _uiState.value.copy(email = email) }
    fun updatePassword(password: String) { _uiState.value = _uiState.value.copy(password = password) }
    fun updateFirstName(firstName: String) { _uiState.value = _uiState.value.copy(firstName = firstName) }
    fun updateLastName(lastName: String) { _uiState.value = _uiState.value.copy(lastName = lastName) }

    fun login() {
        val state = _uiState.value
        viewModelScope.launch { authManager.login(state.email, state.password) }
    }

    fun register() {
        val state = _uiState.value
        viewModelScope.launch { authManager.register(state.email, state.password, state.firstName, state.lastName) }
    }
}

data class AuthUiState(
    val isLogin: Boolean = true,
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val isGoogleLoading: Boolean = false
)