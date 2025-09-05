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
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.domain.usecase.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.*
import android.content.Context
import com.rgbc.cloudBackup.core.security.CryptoManager
import com.rgbc.cloudBackup.core.security.SecurityAuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import androidx.compose.material.icons.filled.Download


@HiltViewModel
class MainViewModel @Inject constructor(
    private val scanFilesUseCase: ScanFilesUseCase,
    private val getAllFilesUseCase: GetAllFilesUseCase,
    private val getFilesToBackupUseCase: GetFilesToBackupUseCase,
    private val insertFileUseCase: InsertFileUseCase,
    private val getBackupStatsUseCase: GetBackupStatisticsUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val downloadFileUseCase: DownloadFileUseCase,
    private val directoryProvider: DirectoryProvider,
    private val cryptoManager: CryptoManager,
    private val auditLogger: SecurityAuditLogger,
    @ApplicationContext private val context: Context
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
                val fileName = "TestFile_$now.txt"
                val safeDisplayName = getSafeDisplayName(fileName)

                Timber.i("Creating real test file: $safeDisplayName")

                // Create actual file in app's internal storage
                val testDir = File(context.filesDir, "test_files")
                testDir.mkdirs()

                val testFile = File(testDir, fileName)

                // Write test content
                val content = "This is a test file created at ${Date()}\n" +
                        "File ID: $now\n" +
                        "This file can be uploaded to test cloud backup functionality."
                testFile.writeText(content)

                // Log file creation (NIST compliance)
                auditLogger.logFileCreation(safeDisplayName, testFile.absolutePath)

                Timber.d("Test file created at: ${testFile.absolutePath}")

                // Create database entry
                val fileIndex = FileIndex(
                    id = 0,
                    name = fileName,
                    fileType = "txt",
                    size = testFile.length(),
                    modifiedAt = Date(now),
                    isBackedUp = false,
                    path = testFile.absolutePath,
                    checksum = "test_$now",
                    createdAt = Date(now)
                )

                insertFileUseCase(fileIndex)
                refreshData()

                _uiState.update {
                    it.copy(errorMessage = "✅ Test file created successfully")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "❌ Add test file failed: ${e.message}")
                }
                Timber.e(e, "Add test file error")
            }
        }
    }


    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun testUploadFile() {
        viewModelScope.launch {
            try {
                val filesToBackup = _filesToBackup.value
                if (filesToBackup.isNotEmpty()) {
                    val newestFile = filesToBackup.maxByOrNull { it.createdAt }
                    if (newestFile != null) {
                        val safeDisplayName = getSafeDisplayName(newestFile.name)
                        Timber.i("Starting upload for file: $safeDisplayName")

                        uploadFileUseCase.execute(newestFile)
                            .catch { e ->
                                Timber.e(e, "Upload failed for file: $safeDisplayName")
                                _uiState.update {
                                    it.copy(errorMessage = "Upload failed: ${e.message}")
                                }
                            }
                            .collect { progress ->
                                Timber.d("Upload progress for $safeDisplayName: $progress")
                                when (progress) {
                                    is UploadProgress.Starting -> {
                                        _uiState.update {
                                            it.copy(errorMessage = "📤 Starting upload...")
                                        }
                                    }
                                    is UploadProgress.Encrypting -> {
                                        _uiState.update {
                                            it.copy(errorMessage = "🔐 Encrypting file... (${(progress.progress * 100).toInt()}%)")
                                        }
                                    }
                                    is UploadProgress.Uploading -> {
                                        _uiState.update {
                                            it.copy(errorMessage = "📤 Uploading... (${(progress.progress * 100).toInt()}%)")
                                        }
                                    }
                                    is UploadProgress.Completed -> {
                                        _uiState.update {
                                            it.copy(errorMessage = "✅ Upload completed successfully")
                                        }
                                        refreshData()
                                    }
                                    is UploadProgress.Failed -> {
                                        _uiState.update {
                                            it.copy(errorMessage = "❌ Upload failed: ${progress.error}")
                                        }
                                    }
                                }
                            }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Upload process failed")
                _uiState.update {
                    it.copy(errorMessage = "Upload failed: ${e.message}")
                }
            }
        }
    }


    private fun getSafeDisplayName(fileName: String): String {
        return "File_${fileName.hashCode().toString().takeLast(8)}"
    }

    fun testDownloadFile() {
        viewModelScope.launch {
            val backedUp = _allFiles.value.filter { it.isBackedUp }
            if (backedUp.isEmpty()) {
                _uiState.update { it.copy(errorMessage = "No backed up files.") }
                return@launch
            }
            val fileToDownload = backedUp.first()
            val safeName = getSafeDisplayName(fileToDownload.name)

            val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }

            downloadFileUseCase.execute(fileToDownload, downloadsDir)
                .catch { e ->
                    _uiState.update { it.copy(errorMessage = "Download failed: ${e.message}") }
                }
                .collect { progress ->
                    when (progress) {
                        is DownloadProgress.Starting ->
                            _uiState.update { it.copy(errorMessage = "📥 Starting download...") }
                        is DownloadProgress.Downloading ->
                            _uiState.update {
                                it.copy(
                                    errorMessage = "📥 Downloading encrypted file... (${(progress.progress * 100).toInt()}%)"
                                )
                            }
                        is DownloadProgress.Decrypting ->
                            _uiState.update { it.copy(errorMessage = "🔐 Decrypting file...") }
                        is DownloadProgress.Completed ->
                            _uiState.update {
                                it.copy(
                                    errorMessage = "✅ Download & decryption complete: ${progress.outputPath}"
                                )
                            }
                        is DownloadProgress.Failed ->
                            _uiState.update {
                                it.copy(errorMessage = "❌ Download failed: ${progress.error}")
                            }
                    }
                }
        }
    }




}

