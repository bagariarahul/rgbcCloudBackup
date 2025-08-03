// File: MainViewModel.kt
package com.rgbc.cloudBackup.features.main.presentation

import com.rgbc.cloudBackup.core.domain.usecase.GetBackupStatisticsUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.usecase.*
import com.rgbc.cloudBackup.core.utils.DirectoryProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val scanFilesUseCase: ScanFilesUseCase,
    private val getAllFilesUseCase: GetAllFilesUseCase,
    private val getFilesToBackupUseCase: GetFilesToBackupUseCase,
    private val insertFileUseCase: InsertFileUseCase,
    private val getBackupStatsUseCase: GetBackupStatisticsUseCase,
    private val directoryProvider: DirectoryProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _allFiles = MutableStateFlow<List<FileIndex>>(emptyList())
    val allFiles: StateFlow<List<FileIndex>> = _allFiles.asStateFlow()

    private val _filesToBackup = MutableStateFlow<List<FileIndex>>(emptyList())
    val filesToBackup: StateFlow<List<FileIndex>> = _filesToBackup.asStateFlow()

    init {
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(errorMessage = null) }
                _allFiles.value = getAllFilesUseCase().first()
                _filesToBackup.value = getFilesToBackupUseCase().first()
                val stats = getBackupStatsUseCase()
                _uiState.update { it.copy(backupStatistics = stats) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Refresh failed: ${e.message}") }
                Timber.e(e, "Failed to refresh data")
            }
        }
    }

    fun startFileScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = null) }
            scanFilesUseCase.executeWithProgress(directoryProvider.getScannableDirectories())
                .onEach { progress ->
                    _uiState.update { it.copy(scanProgress = progress) }
                }
                .launchIn(this)
                .invokeOnCompletion {
                    _uiState.update { it.copy(isScanning = false) }
                }
        }
    }

    fun addTestFile() {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val test = FileIndex(
                    id = 0,
                    name = "TestFile_$now.txt",
                    fileType = "txt",
                    size = 1024,
                    modifiedAt = Date(now),
                    isBackedUp = false,
                    path = "/test/TestFile_$now.txt",
                    checksum = "test_$now",
                    createdAt = Date(now)
                )
                insertFileUseCase(test)
                refreshData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Add test file failed: ${e.message}") }
                Timber.e(e, "Add test file error")
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
