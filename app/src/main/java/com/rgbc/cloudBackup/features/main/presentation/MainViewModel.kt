package com.rgbc.cloudBackup.features.main.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.usecase.GetAllFilesUseCase
import com.rgbc.cloudBackup.core.domain.usecase.GetFilesToBackupUseCase
import com.rgbc.cloudBackup.core.domain.usecase.GetBackupStatisticsUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadFileUseCase
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import com.rgbc.cloudBackup.core.domain.model.BackupStats
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.rgbc.cloudBackup.core.domain.usecase.UploadProgress
import java.io.File
import java.io.FileOutputStream
import java.util.Date

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getAllFilesUseCase: GetAllFilesUseCase,
    private val getFilesToBackupUseCase: GetFilesToBackupUseCase,
    private val getBackupStatsUseCase: GetBackupStatisticsUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val backupApiService: BackupApiService, // DIRECT: Inject API service directly
    private val fileRepository: FileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // UI State management
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Database-backed state flows
    private val _allFiles = MutableStateFlow<List<FileIndex>>(emptyList())
    val allFiles: StateFlow<List<FileIndex>> = _allFiles.asStateFlow()

    private val _filesToBackup = MutableStateFlow<List<FileIndex>>(emptyList())
    val filesToBackup: StateFlow<List<FileIndex>> = _filesToBackup.asStateFlow()

    // UI State for display
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Upload progress tracking
    private val _uploadProgress = MutableStateFlow<Map<String, LocalUploadProgress>>(emptyMap())
    val uploadProgress: StateFlow<Map<String, LocalUploadProgress>> = _uploadProgress.asStateFlow()

    // Statistics
    private val _backupStatistics = MutableStateFlow(BackupStats())
    val backupStatistics: StateFlow<BackupStats> = _backupStatistics.asStateFlow()

    // Flag to prevent database observation conflicts during operations
    private var operationInProgress = false

    init {
        Timber.d("🔧 MainViewModel initialized")
        startDatabaseObservation()
        refreshData()
    }

    // Database observation with operation conflict prevention
    private fun startDatabaseObservation() {
        // Observe all files
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getAllFilesUseCase().collect { files ->
                    // Skip update if operation in progress to prevent conflicts
                    if (!operationInProgress) {
                        withContext(Dispatchers.Main) {
                            _allFiles.value = files
                            updateDisplayFiles(files)
                            Timber.d("📊 Real-time file update: ${files.size} files, ${files.count { it.isBackedUp }} backed up")
                        }
                    }

                    // Update statistics when files change
                    try {
                        val stats = getBackupStatsUseCase()
                        withContext(Dispatchers.Main) {
                            _backupStatistics.value = stats
                            if (!operationInProgress) {
                                _uiState.update { it.copy(backupStatistics = stats) }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to update statistics")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Database observation failed")
                withContext(Dispatchers.Main) {
                    _error.value = "Database observation failed: ${e.message}"
                    if (!operationInProgress) {
                        _uiState.update { it.copy(errorMessage = "Database observation failed: ${e.message}") }
                    }
                }
            }
        }

        // Also observe pending files
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getFilesToBackupUseCase().collect { pendingFiles ->
                    if (!operationInProgress) {
                        withContext(Dispatchers.Main) {
                            _filesToBackup.value = pendingFiles
                            Timber.d("📋 Pending files update: ${pendingFiles.size} files")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Pending files observation failed")
            }
        }
    }

    // Convert FileIndex to FileItem for UI display
    private fun updateDisplayFiles(fileIndexList: List<FileIndex>) {
        val fileItems = fileIndexList.map { fileIndex ->
            FileItem(
                id = fileIndex.id.toString(),
                name = fileIndex.name,
                size = fileIndex.size,
                mimeType = fileIndex.mimeType ?: "application/octet-stream",
                path = fileIndex.path,
                lastModified = fileIndex.modifiedAt?.time ?: fileIndex.createdAt.time,
                isUploaded = fileIndex.isBackedUp,
                uploadProgress = if (fileIndex.isBackedUp) 1f else 0f,
                directoryName = fileIndex.fileType ?: "",
                isImage = fileIndex.mimeType?.startsWith("image/") == true,
                isVideo = fileIndex.mimeType?.startsWith("video/") == true,
                isDocument = fileIndex.mimeType?.let {
                    it.contains("pdf") || it.contains("document") || it.contains("text")
                } == true
            )
        }
        _files.value = fileItems
    }

    // Upload with proper database persistence
    fun uploadSingleFile(fileIndex: FileIndex) {
        viewModelScope.launch {
            try {
                operationInProgress = true // Prevent database conflicts

                Timber.d("📤 Starting upload for: ${fileIndex.name}")
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                // Convert to actual file for upload
                val actualFile = convertUriToFile(fileIndex.path, fileIndex.name)
                if (actualFile == null || !actualFile.exists()) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "File not accessible: ${fileIndex.name}"
                        )
                    }
                    operationInProgress = false
                    return@launch
                }

                // Start upload with progress tracking
                uploadFileUseCase.execute(actualFile).collect { progress ->
                    when (progress::class.simpleName) {
                        "Starting" -> {
                            Timber.d("📤 Upload starting: ${fileIndex.name}")
                            _uploadProgress.value = _uploadProgress.value + (fileIndex.id.toString() to
                                    LocalUploadProgress.InProgress(5, 0, actualFile.length()))
                        }
                        "Uploading" -> {
                            Timber.d("📤 Uploading: ${fileIndex.name}")
                            _uploadProgress.value = _uploadProgress.value + (fileIndex.id.toString() to
                                    LocalUploadProgress.InProgress(75, actualFile.length() / 2, actualFile.length()))
                        }
                        "Completed" -> {
                            _uploadProgress.value = _uploadProgress.value - fileIndex.id.toString()

                            // Actually update database
                            try {

                                val serverFileId = extractServerFileIdFromProgress(progress)
                                Timber.w("$serverFileId is returned")

                                if (serverFileId != null) {
                                    // Store server file ID in database for future downloads
                                    fileRepository.updateServerFileId(fileIndex.id, serverFileId)
                                    Timber.i("✅ Server file ID stored: ${fileIndex.id} → $serverFileId")
                                }


                                fileRepository.markAsBackedUp(fileIndex.id)
                                Timber.i("✅ Database updated - file marked as backed up: ${fileIndex.name}")

                                // Update local state to match database
                                val updatedFiles = _allFiles.value.map { file ->
                                    if (file.id == fileIndex.id) {
                                        file.copy(isBackedUp = true, backedUpAt = Date(),serverFileId = serverFileId)
                                    } else {
                                        file
                                    }
                                }
                                _allFiles.value = updatedFiles
                                updateDisplayFiles(updatedFiles)

                            } catch (e: Exception) {
                                Timber.e(e, "❌ Failed to update database for file: ${fileIndex.name}")
                                _uiState.update {
                                    it.copy(errorMessage = "Upload succeeded but failed to update database: ${e.message}")
                                }
                            }

                            // Clean up temp file
                            try {
                                actualFile.delete()
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to delete temp file")
                            }

                            // Update UI state
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    successMessage = "✅ Upload completed: ${fileIndex.name}"
                                )
                            }

                            // Clear success message after delay
                            delay(3000)
                            _uiState.update { it.copy(successMessage = null) }

                            Timber.i("📤 ✅ Upload completed and persisted: ${fileIndex.name}")
                        }
                        "Failed" -> {
                            _uploadProgress.value = _uploadProgress.value - fileIndex.id.toString()
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    errorMessage = "❌ Upload failed: Unknown error"
                                )
                            }
                            Timber.e("📤 ❌ Upload failed: ${fileIndex.name}")
                        }
                        else -> {
                            Timber.d("📤 Upload state: ${progress::class.simpleName} for ${fileIndex.name}")
                        }
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Upload error: ${e.message}"
                    )
                }
                Timber.e(e, "Upload error for: ${fileIndex.name}")
            } finally {
                // Re-enable database observation after delay
                delay(2000)
                operationInProgress = false
                Timber.d("🔄 Database observation re-enabled")
            }
        }
    }

    // SIMPLIFIED: Download with direct API call (no use case dependency issues)
    fun downloadSingleFile(fileIndex: FileIndex) {
        viewModelScope.launch {
            try {
                val safeDisplayName = getSafeDisplayName(fileIndex.name)

                // Check if file is actually backed up
                if (!fileIndex.isBackedUp) {
                    _uiState.update {
                        it.copy(errorMessage = "❌ File is not backed up yet. Upload it first.")
                    }
                    Timber.w("⚠️ Download attempted on non-backed-up file: $safeDisplayName")
                    return@launch
                }

                operationInProgress = true
                Timber.i("📥 Starting DIRECT download for: $safeDisplayName (ID: ${fileIndex.id})")
                _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

                // Use proper Downloads directory with fallbacks
                val downloadsDir = try {
                    // Try external storage Downloads directory first
                    val externalDownloads = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "CloudBackup")
                    if (externalDownloads.exists() || externalDownloads.mkdirs()) {
                        externalDownloads
                    } else {
                        // Fallback to app-specific external files
                        File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "CloudBackup").apply { mkdirs() }
                    }
                } catch (e: Exception) {
                    // Ultimate fallback to internal storage
                    File(context.filesDir, "downloads/CloudBackup").apply { mkdirs() }
                }

                Timber.d("📁 Download directory: ${downloadsDir.absolutePath}")

                try {

                    val downloadFileId = if (fileIndex.serverFileId != null) {
                        Timber.d("📥 Using server file ID for download: ${fileIndex.serverFileId}")
                        fileIndex.serverFileId.toString()
                    } else {
                        Timber.w("📥 No server file ID available, using database ID: ${fileIndex.id}")
                        fileIndex.id.toString()
                    }

                    // DIRECT API CALL: Call BackupApiService directly
                    val fileId = fileIndex.id.toString()
                    Timber.d("📥 Calling server API directly for file ID: $fileId")

                    _uiState.update { it.copy(errorMessage = "📥 Downloading from server...") }

                    val response = withContext(Dispatchers.IO) {
                        backupApiService.downloadFileById(fileId)
                    }

                    if (response.isSuccessful && response.body() != null) {
                        val responseBody = response.body()!!
                        val outputFile = File(downloadsDir, safeDisplayName)

                        Timber.d("📥 Server response successful, saving to: ${outputFile.absolutePath}")
                        _uiState.update { it.copy(errorMessage = "📥 Saving file...") }

                        // Write response body to file
                        withContext(Dispatchers.IO) {
                            responseBody.byteStream().use { inputStream ->
                                outputFile.outputStream().use { outputStream ->
                                    val buffer = ByteArray(8192)
                                    var totalBytes = 0L
                                    var bytes = inputStream.read(buffer)

                                    while (bytes != -1) {
                                        outputStream.write(buffer, 0, bytes)
                                        totalBytes += bytes
                                        bytes = inputStream.read(buffer)
                                    }

                                    outputStream.flush()
                                }
                            }
                        }

                        // Verify file was created and has content
                        if (outputFile.exists() && outputFile.length() > 0) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    successMessage = "📥 ✅ Download completed!\nFile: ${outputFile.name}\nSize: ${outputFile.length()} bytes\nPath: ${outputFile.absolutePath}",
                                    errorMessage = null
                                )
                            }

                            Timber.i("📥 ✅ DIRECT download completed successfully!")
                            Timber.i("📁 File: ${outputFile.absolutePath}")
                            Timber.i("📊 Size: ${outputFile.length()} bytes")
                            Timber.i("🆔 Used file ID: $downloadFileId")


                            // Read and log first few bytes to verify content
                            try {
                                val preview = outputFile.readText().take(100)
                                Timber.i("📄 Content preview: $preview")
                            } catch (e: Exception) {
                                Timber.i("📄 File is binary or encrypted (cannot preview as text)")
                            }

                            // Clear success message after longer delay
                            delay(12000)
                            _uiState.update { it.copy(successMessage = null) }

                        } else {
                            throw Exception("Downloaded file is empty or not created")
                        }

                    } else {
                        val errorMsg = "Server error: ${response.code()} - ${response.message()}"
                        throw Exception(errorMsg)
                    }

                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "📥 ❌ Download failed: ${e.message}",
                            successMessage = null
                        )
                    }

                    Timber.e(e, "📥 ❌ DIRECT download failed")

                    // Clear error after delay
                    delay(7000)
                    _uiState.update { it.copy(errorMessage = null) }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "📥 ❌ Download failed: ${e.message}",
                        successMessage = null
                    )
                }
                Timber.e(e, "📥 ❌ Download exception for: ${fileIndex.name}")

                // Clear error after delay
                delay(5000)
                _uiState.update { it.copy(errorMessage = null) }
            } finally {
                // Re-enable database observation
                delay(1000)
                operationInProgress = false
            }
        }
    }

    // Delete single file
    fun deleteSingleFile(fileIndex: FileIndex) {
        viewModelScope.launch {
            try {
                val safeDisplayName = getSafeDisplayName(fileIndex.name)

                Timber.d("🗑️ Delete requested for: $safeDisplayName")
                _uiState.update { it.copy(isLoading = true) }

                // Try to delete from database via repository
                try {
                    fileRepository.deleteFile(fileIndex)
                    Timber.d("✅ File deleted from database: $safeDisplayName")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to delete from database, removing from local state only")
                }

                // Remove from local state
                val updatedFiles = _allFiles.value.filter { it.id != fileIndex.id }
                _allFiles.value = updatedFiles
                updateDisplayFiles(updatedFiles)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "🗑️ ✅ File deleted: $safeDisplayName"
                    )
                }

                // Clear message after delay
                delay(2000)
                _uiState.update { it.copy(successMessage = null) }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "🗑️ ❌ Delete failed: ${e.message}"
                    )
                }
                Timber.e(e, "Delete failed for: ${fileIndex.name}")
            }
        }
    }

    // Data refresh with proper error handling
    fun refreshData() {
        viewModelScope.launch {
            try {
                if (!operationInProgress) {
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                }
                _isLoading.value = true
                Timber.d("🔄 Refreshing data from database...")

                delay(500)

                val freshFiles = getAllFilesUseCase().first()
                val freshPending = getFilesToBackupUseCase().first()
                val freshStats = getBackupStatsUseCase()

                if (!operationInProgress) {
                    _allFiles.value = freshFiles
                    _filesToBackup.value = freshPending
                    _backupStatistics.value = freshStats
                    updateDisplayFiles(freshFiles)

                    // Update UI state
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            backupStatistics = freshStats,
                            lastRefreshTime = System.currentTimeMillis()
                        )
                    }
                }

                Timber.d("✅ Refresh complete: ${freshFiles.size} files, ${freshStats.backedUpFiles} backed up")
                clearError()

            } catch (e: Exception) {
                _error.value = "Failed to refresh data: ${e.message}"
                if (!operationInProgress) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to refresh: ${e.message}"
                        )
                    }
                }
                Timber.e(e, "Data refresh failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Content URI to file conversion
    private suspend fun convertUriToFile(uriString: String, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            when {
                uriString.startsWith("file://") -> {
                    File(Uri.parse(uriString).path ?: uriString)
                }
                uriString.startsWith("content://") -> {
                    val uri = Uri.parse(uriString)
                    val documentFile = DocumentFile.fromSingleUri(context, uri)

                    if (documentFile?.exists() != true) {
                        return@withContext null
                    }

                    val tempFile = File(context.cacheDir, "temp_$fileName")
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(tempFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    tempFile
                }
                else -> {
                    File(uriString)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to convert URI to file: $uriString")
            null
        }
    }

    // Helper function to generate safe display names
    private fun getSafeDisplayName(originalName: String): String {
        return try {
            if (originalName.isBlank()) {
                "Unknown_File"
            } else {
                "File_${originalName.hashCode().toString().takeLast(8)}"
            }
        } catch (e: Exception) {
            "File_${System.currentTimeMillis().toString().takeLast(8)}"
        }
    }

    // External refresh trigger
    fun triggerExternalRefresh(source: String) {
        if (!operationInProgress) {
            viewModelScope.launch {
                try {
                    Timber.d("🔄 External refresh triggered from: $source")
                    _uiState.update { it.copy(isLoading = true, errorMessage = null) }

                    delay(500)
                    refreshData()

                } catch (e: Exception) {
                    Timber.e(e, "External refresh failed from $source")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Refresh failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    // Force refresh function
    fun forceDataRefresh() {
        Timber.d("🔄 Force refresh requested")
        if (!operationInProgress) {
            refreshData()
        } else {
            Timber.d("🔄 Force refresh skipped - operation in progress")
        }
    }

    // Directory operation completion notification
    fun notifyDirectoryOperationComplete() {
        viewModelScope.launch {
            Timber.d("📢 Notified of directory operation completion")
            delay(1000)
            forceDataRefresh()
        }
    }

    // Clear errors
    fun clearError() {
        _error.value = null
        if (!operationInProgress) {
            _uiState.update { it.copy(errorMessage = null) }
        }
    }

    // Statistics helpers
    fun getUploadedFileCount(): Int = _allFiles.value.count { it.isBackedUp }
    fun getTotalFileSize(): Long = _allFiles.value.sumOf { it.size }
    fun getTotalFileCount(): Int = _allFiles.value.size

    override fun onCleared() {
        super.onCleared()
        Timber.d("🧹 MainViewModel cleared")
    }
}

private fun extractServerFileIdFromProgress(progress: Any): Long? {
    return try {
        when (progress) {
            is UploadProgress.Completed -> {
                // Direct access - no reflection needed
                val serverId = progress.serverId.toLongOrNull()
                Timber.d("📝 Found serverId in Completed: '${progress.serverId}' → $serverId")
                serverId
            }
            else -> {
                Timber.d("🚫 Progress type '${progress::class.simpleName}' does not contain server ID")
                null
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "❌ Could not extract server file ID from progress")
        null
    }
}


// Simplified UI State data class
//data class MainUiState(
//    val isScanning: Boolean = false,
//    val isLoading: Boolean = false,
//    val backupStatistics: BackupStats = BackupStats(),
//    val errorMessage: String? = null,
//    val successMessage: String? = null,
//    val lastRefreshTime: Long = 0L
//)

// FileItem for UI display
data class FileItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val path: String,
    val lastModified: Long,
    val isUploaded: Boolean = false,
    val uploadProgress: Float = 0f,
    val directoryName: String = "",
    val isImage: Boolean = false,
    val isVideo: Boolean = false,
    val isDocument: Boolean = false
)

// Local UploadProgress states
sealed class LocalUploadProgress {
    object Idle : LocalUploadProgress()
    data class InProgress(val percentage: Int, val bytesUploaded: Long, val totalBytes: Long) : LocalUploadProgress()
    data class Completed(val serverId: String, val serverFileName: String) : LocalUploadProgress()
    data class Failed(val error: String) : LocalUploadProgress()
}