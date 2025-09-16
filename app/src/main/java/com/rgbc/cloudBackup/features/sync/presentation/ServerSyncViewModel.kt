package com.rgbc.cloudBackup.features.sync.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.domain.repository.ServerSyncRepository
import com.rgbc.cloudBackup.core.domain.repository.SyncComparisonResult
import com.rgbc.cloudBackup.core.network.api.ServerFileInfo
import com.rgbc.cloudBackup.core.network.api.FileIntegrityInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ServerSyncViewModel @Inject constructor(
    private val serverSyncRepository: ServerSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerSyncUiState())
    val uiState: StateFlow<ServerSyncUiState> = _uiState.asStateFlow()

    private val _serverFiles = MutableStateFlow<List<ServerFileInfo>>(emptyList())
    val serverFiles: StateFlow<List<ServerFileInfo>> = _serverFiles.asStateFlow()

    private val _syncComparison = MutableStateFlow<SyncComparisonResult?>(null)
    val syncComparison: StateFlow<SyncComparisonResult?> = _syncComparison.asStateFlow()

    private val _integrityResults = MutableStateFlow<Map<String, FileIntegrityInfo>>(emptyMap())
    val integrityResults: StateFlow<Map<String, FileIntegrityInfo>> = _integrityResults.asStateFlow()

    init {
        Timber.d("🔧 ServerSyncViewModel initialized")
    }

    fun refreshServerData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                Timber.d("🔄 Starting server data refresh")

                // Get server files
                val serverResult = serverSyncRepository.getServerFiles()
                if (serverResult.isSuccess) {
                    val files = serverResult.getOrNull() ?: emptyList()
                    _serverFiles.value = files
                    Timber.d("✅ Server files loaded: ${files.size}")
                } else {
                    val error = serverResult.exceptionOrNull()?.message ?: "Failed to load server files"
                    _uiState.value = _uiState.value.copy(error = error)
                    Timber.e("❌ Server files failed: $error")
                    return@launch
                }

                // Compare with local files
                val comparisonResult = serverSyncRepository.compareWithLocal()
                if (comparisonResult.isSuccess) {
                    _syncComparison.value = comparisonResult.getOrNull()
                    Timber.d("✅ Sync comparison completed")
                } else {
                    val error = comparisonResult.exceptionOrNull()?.message ?: "Sync comparison failed"
                    Timber.e("❌ Sync comparison failed: $error")
                }

                _uiState.value = _uiState.value.copy(isLoading = false)

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Refresh failed: ${e.message}"
                )
                Timber.e(e, "❌ Server refresh failed")
            }
        }
    }

    fun checkFileIntegrity(fileName: String) {
        viewModelScope.launch {
            try {
                Timber.d("🔍 Checking integrity for: $fileName")
                val result = serverSyncRepository.checkFileIntegrity(fileName)

                if (result.isSuccess) {
                    val integrity = result.getOrNull()!!
                    _integrityResults.value = _integrityResults.value + (fileName to integrity)
                    Timber.d("✅ Integrity check complete for $fileName: valid=${integrity.isValid}")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Integrity check failed"
                    _uiState.value = _uiState.value.copy(error = "Integrity check failed for $fileName: $error")
                    Timber.e("❌ Integrity check failed for $fileName: $error")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Integrity check failed: ${e.message}")
                Timber.e(e, "❌ Integrity check error for $fileName")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class ServerSyncUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)
