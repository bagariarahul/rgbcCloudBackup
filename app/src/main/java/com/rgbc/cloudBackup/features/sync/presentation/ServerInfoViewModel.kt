package com.rgbc.cloudBackup.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import com.rgbc.cloudBackup.core.network.api.ServerInfoResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ServerInfoUiState(
    val isLoading: Boolean = false,
    val serverInfo: ServerInfoResponse? = null,
    val error: String? = null
)

@HiltViewModel
class ServerInfoViewModel @Inject constructor(
    private val apiService: BackupApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerInfoUiState())
    val uiState: StateFlow<ServerInfoUiState> = _uiState.asStateFlow()

    init {
        fetchServerInfo()
    }

    fun fetchServerInfo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val response = apiService.getServerInfo()

                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = ServerInfoUiState(
                        isLoading = false,
                        serverInfo = response.body()
                    )
                    Timber.i("🌐 Server info fetched: ${response.body()?.status}")
                } else {
                    _uiState.value = ServerInfoUiState(
                        isLoading = false,
                        error = "Server error: ${response.code()} — ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "🌐 Failed to fetch server info")
                _uiState.value = ServerInfoUiState(
                    isLoading = false,
                    error = "Connection failed: ${e.message}"
                )
            }
        }
    }
}