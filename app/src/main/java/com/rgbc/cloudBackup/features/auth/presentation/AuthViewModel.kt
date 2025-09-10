package com.rgbc.cloudBackup.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.auth.AuthManager
import com.rgbc.cloudBackup.core.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    // Expose AuthManager's state
    val authState: StateFlow<AuthState> = authManager.authState

    // UI state for form management
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun setLoginMode(isLogin: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLogin = isLogin,
            // Clear form when switching modes
            email = "",
            password = "",
            firstName = "",
            lastName = ""
        )
    }

    fun updateEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun updateFirstName(firstName: String) {
        _uiState.value = _uiState.value.copy(firstName = firstName)
    }

    fun updateLastName(lastName: String) {
        _uiState.value = _uiState.value.copy(lastName = lastName)
    }

    fun login() {
        val state = _uiState.value
        viewModelScope.launch {
            authManager.login(state.email, state.password)
        }
    }

    fun register() {
        val state = _uiState.value
        viewModelScope.launch {
            authManager.register(state.email, state.password, state.firstName, state.lastName)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authManager.logout()
        }
    }
}

data class AuthUiState(
    val isLogin: Boolean = true,
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = ""
)