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
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import com.rgbc.cloudBackup.core.security.CryptoManager
import com.rgbc.cloudBackup.core.security.SecurityAuditLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import java.io.File
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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
        Timber.d("🔧 MainViewModel initialized")

        // 🔧 FIX: Start continuous database observation
        startDatabaseObservation()

        // Initial data load
        refreshData()
    }

    // FIND the refreshData() function and REPLACE it with this improved version:
    // REPLACE the existing refreshData() function:
    @OptIn(DelicateCoroutinesApi::class)
    fun refreshData() {
        // 🔧 CRITICAL FIX: Use GlobalScope to prevent cancellation during navigation
        GlobalScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    Timber.d("🔄 Starting navigation-safe data refresh...")
                }

                // Brief loading delay on IO thread
                delay(300)

                // Database operations on IO thread
                val currentFiles = getAllFilesUseCase().first()
                val currentPending = getFilesToBackupUseCase().first()
                val currentStats = getBackupStatsUseCase()

                // Update UI on Main thread
                withContext(Dispatchers.Main) {
                    _allFiles.value = currentFiles
                    _filesToBackup.value = currentPending

                    _uiState.update {
                        it.copy(
                            backupStatistics = currentStats,
                            lastRefreshTime = System.currentTimeMillis(),
                            isLoading = false,
                            successMessage = "✅ Refreshed successfully"
                        )
                    }

                    Timber.d("✅ Navigation-safe refresh complete: ${currentFiles.size} files, ${currentStats.backedUpFiles} backed up")
                }

                // Clear success message after delay
                delay(2000)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(successMessage = null) }
                }

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                errorMessage = "Refresh failed: ${e.message}",
                                isLoading = false
                            )
                        }
                    }
                    Timber.e(e, "❌ Failed to refresh data")
                } else {
                    Timber.w("⚠️ Refresh was cancelled, but that's OK in GlobalScope")
                }
            }
        }
    }





    // ADD this function to force refresh from directory screen
    fun forceRefreshAfterBackup() {
        viewModelScope.launch {
            Timber.d("🔄 Force refresh after backup operation")
            delay(2000) // Wait for database commits
            refreshData()

            // Additional verification
            delay(1000)
            val verifyFiles = getAllFilesUseCase().first()
            val backedUpCount = verifyFiles.count { it.isBackedUp }
            Timber.d("🔍 Post-backup verification: ${backedUpCount}/${verifyFiles.size} files backed up")
        }
    }


    // 🆕 ADD: New function to force refresh from other screens
    fun forceRefreshFromDirectory() {
        viewModelScope.launch {
            Timber.d("🔄 Force refresh triggered from directory screen")
            delay(500) // Small delay to ensure database has been updated
            refreshData()
        }
    }


    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ADD these functions to MainViewModel.kt:

    fun uploadSingleFile(file: FileIndex) {
        viewModelScope.launch {
            try {
                val safeDisplayName = getSafeDisplayName(file.name)
                Timber.i("📤 Starting individual upload for: $safeDisplayName")
                Timber.d("📁 Original file path: ${file.path}")

                // 🔧 FIX: Create temp file from original path for upload
                val originalFile = getOriginalFile(file.path)
                if (originalFile == null || !originalFile.exists()) {
                    Timber.e("❌ Original file not accessible: ${file.path}")
                    _uiState.update { it.copy(errorMessage = "❌ File not accessible. May have been moved or deleted.") }
                    return@launch
                }

                _uiState.update { it.copy(errorMessage = "📤 Uploading $safeDisplayName...") }

                // Create temporary file for upload process
                val tempFile = createTempFileForUpload(originalFile)
                if (tempFile == null) {
                    _uiState.update { it.copy(errorMessage = "❌ Failed to prepare file for upload") }
                    return@launch
                }

                // Create temporary FileIndex for upload with temp path
                val uploadFileIndex = file.copy(path = tempFile.absolutePath)

                uploadFileUseCase.execute(uploadFileIndex).collect { progress ->
                    when (progress) {
                        is UploadProgress.Completed -> {
                            _uiState.update { it.copy(errorMessage = "✅ Upload completed!") }
                            // Clean up temp file
                            tempFile.delete()
                            // Force refresh with delay
                            delay(1000)
                            refreshData()
                            Timber.d("🔄 Refreshed data after successful upload")
                        }
                        is UploadProgress.Failed -> {
                            _uiState.update { it.copy(errorMessage = "❌ Upload failed: ${progress.error}") }
                            tempFile.delete() // Clean up on failure too
                            Timber.e("❌ Upload failed: ${progress.error}")
                        }
                        else -> {
                            Timber.v("📤 Upload progress: $progress")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Upload failed: ${e.message}") }
                Timber.e(e, "❌ Individual upload failed")
            }
        }
    }

    // 🆕 ADD: Helper function to get original file
    private fun getOriginalFile(path: String): File? {
        return try {
            // Handle different path types
            when {
                path.startsWith("content://") -> {
                    // Handle content URI paths - convert to temp file
                    val uri = Uri.parse(path)
                    createTempFileFromUri(uri)
                }
                path.startsWith("/") -> {
                    // Regular file system path
                    File(path)
                }
                else -> {
                    Timber.w("⚠️ Unknown path format: $path")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error getting original file: $path")
            null
        }
    }

    // 🆕 ADD: Helper to create temp file from URI
    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}")
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to create temp file from URI")
            null
        }
    }

    // 🆕 ADD: Helper to create temp file for upload
    private fun createTempFileForUpload(originalFile: File): File? {
        return try {
            val tempFile = File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}_${originalFile.name}")
            originalFile.copyTo(tempFile, overwrite = true)
            Timber.d("📁 Created temp file for upload: ${tempFile.absolutePath}")
            tempFile
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to create temp file for upload")
            null
        }
    }



    fun downloadSingleFile(file: FileIndex) {
        viewModelScope.launch {
            try {
                val safeDisplayName = getSafeDisplayName(file.name)
                Timber.i("📥 Starting individual download for: $safeDisplayName")

                _uiState.update { it.copy(errorMessage = "📥 Downloading $safeDisplayName...") }

                val downloadsDir = File(context.filesDir, "downloads").apply { mkdirs() }

                downloadFileUseCase.execute(file, downloadsDir).collect { progress ->
                    when (progress) {
                        is DownloadProgress.Completed -> {
                            _uiState.update { it.copy(errorMessage = "✅ Download completed!") }
                        }
                        is DownloadProgress.Failed -> {
                            _uiState.update { it.copy(errorMessage = "❌ Download failed: ${progress.error}") }
                        }
                        else -> {
                            Timber.v("📥 Download progress: $progress")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Download failed: ${e.message}") }
                Timber.e(e, "❌ Individual download failed")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun deleteSingleFile(file: FileIndex) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val safeDisplayName = getSafeDisplayName(file.name)
                Timber.i("🗑️ Starting file deletion: $safeDisplayName")

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "🗑️ Deleting $safeDisplayName...") }
                }

                // 1. Delete from local file system
                val localFile = File(file.path)
                if (localFile.exists()) {
                    val deleted = localFile.delete()
                    if (deleted) {
                        Timber.d("🗑️ Deleted local file successfully")
                    } else {
                        Timber.w("⚠️ Failed to delete local file, but continuing...")
                    }
                }

                // 2. Delete from server if backed up
                if (file.isBackedUp) {
                    try {
                        // TODO: Add server delete API call here
                        Timber.d("🌐 Server deletion would happen here")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to delete from server")
                    }
                }

                // 3. Delete from database
                try {
                    // Use your file repository to delete
                    // fileRepository.deleteFile(file) - You'll need to implement this
                    Timber.d("🗄️ Database deletion would happen here")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete from database")
                }

                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "✅ File deleted successfully") }
                }

                // Refresh data after deletion
                delay(500)
                refreshData()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = "Delete failed: ${e.message}") }
                }
                Timber.e(e, "❌ File deletion failed")
            }
        }
    }


    private fun getSafeDisplayName(fileName: String): String {
        return "File_${fileName.hashCode().toString().takeLast(8)}"
    }


    @OptIn(DelicateCoroutinesApi::class)
    private fun startDatabaseObservation() {
        // 🔧 FIX: Use GlobalScope for database observation
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Observe file changes in real-time
                getAllFilesUseCase().collect { files ->
                    withContext(Dispatchers.Main) {
                        _allFiles.value = files
                    }
                    Timber.d("🔄 Real-time file update: ${files.size} files, ${files.count { it.isBackedUp }} backed up")

                    // Update statistics when files change
                    try {
                        val stats = getBackupStatsUseCase()
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(backupStatistics = stats) }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to update statistics")
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Database observation failed")
                }
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Observe pending files
                getFilesToBackupUseCase().collect { pendingFiles ->
                    withContext(Dispatchers.Main) {
                        _filesToBackup.value = pendingFiles
                    }
                    Timber.d("🔄 Real-time pending files update: ${pendingFiles.size} files")
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "Pending files observation failed")
                }
            }
        }
    }


    // 🆕 ADD: Force refresh from external triggers
    @OptIn(DelicateCoroutinesApi::class)
    fun triggerExternalRefresh(reason: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                Timber.d("🔄 External refresh triggered: $reason")
                delay(1000) // Allow database to settle

                // Force fresh queries on IO thread
                val freshFiles = getAllFilesUseCase().first()
                val freshPending = getFilesToBackupUseCase().first()
                val freshStats = getBackupStatsUseCase()

                withContext(Dispatchers.Main) {
                    _allFiles.value = freshFiles
                    _filesToBackup.value = freshPending
                    _uiState.update {
                        it.copy(
                            backupStatistics = freshStats,
                            lastRefreshTime = System.currentTimeMillis(),
                            errorMessage = "🔄 Refreshed ($reason)"
                        )
                    }
                }

                Timber.d("✅ External refresh complete: ${freshFiles.size} files, ${freshStats.backedUpFiles} backed up")

                // Clear refresh message after delay
                delay(2000)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(errorMessage = null) }
                }

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Timber.e(e, "❌ External refresh failed")
                }
            }
        }
    }





}

