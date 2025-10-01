package com.rgbc.cloudBackup.features.directory.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.data.repository.PersistentDirectoryRepository
import com.rgbc.cloudBackup.core.domain.usecase.InsertFileUseCase
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.features.directory.data.BackupDirectory
import com.rgbc.cloudBackup.features.main.presentation.MainViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Date
import java.security.MessageDigest

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val persistentDirectoryRepository: PersistentDirectoryRepository,
    private val insertFileUseCase: InsertFileUseCase, // ADD: Database use case
    @ApplicationContext private val context: Context
) : ViewModel() {

    // Existing state flows
    private val _directories = MutableStateFlow<List<BackupDirectory>>(emptyList())
    val directories: StateFlow<List<BackupDirectory>> = _directories.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredFiles = MutableStateFlow<List<DiscoveredFile>>(emptyList())
    val discoveredFiles: StateFlow<List<DiscoveredFile>> = _discoveredFiles.asStateFlow()

    // ADD: Backup progress state
    private val _backupProgress = MutableStateFlow<BackupProgress?>(null)
    val backupProgress: StateFlow<BackupProgress?> = _backupProgress.asStateFlow()

    // ADD: Communication mechanism - injected MainViewModel reference
    // Note: In production, use a shared state manager or event bus instead
    private var mainViewModelRef: MainViewModel? = null

    init {
        Timber.d("🔧 DirectoryViewModel initialized")
        loadDirectories()
        restorePersistedDirectories()
    }

    // ADD: Set MainViewModel reference for communication
    fun setMainViewModelReference(mainViewModel: MainViewModel) {
        this.mainViewModelRef = mainViewModel
        Timber.d("🔗 MainViewModel reference set")
    }

    // Existing methods remain the same...
    private fun loadDirectories() {
        viewModelScope.launch {
            try {
                Timber.d("📂 Loading directories...")
                persistentDirectoryRepository.getAllDirectories().collect { directoryList ->
                    _directories.value = directoryList
                    Timber.d("📂 Loaded ${directoryList.size} directories")
                }
            } catch (e: Exception) {
                _error.value = "Failed to load directories: ${e.message}"
                Timber.e(e, "Failed to load directories")
            }
        }
    }

    private fun restorePersistedDirectories() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val restoredDirectories = persistentDirectoryRepository.restorePersistedDirectories()
                Timber.d("🔄 Restored ${restoredDirectories.size} directories")
            } catch (e: Exception) {
                Timber.w(e, "Failed to restore persisted directories")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addDirectory(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Timber.d("➕ Adding directory: $uri")

                val result = persistentDirectoryRepository.addDirectory(uri)

                if (result.isFailure) {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    _error.value = "Failed to add directory: $errorMsg"
                    Timber.e("Failed to add directory: $errorMsg")
                } else {
                    val directory = result.getOrNull()
                    Timber.d("✅ Directory added successfully: ${directory?.displayName}")
                    clearError()
                }
            } catch (e: Exception) {
                _error.value = "Exception adding directory: ${e.message}"
                Timber.e(e, "Exception adding directory")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Existing scan methods remain the same...
    fun scanDirectory(directory: BackupDirectory) {
        viewModelScope.launch {
            try {
                Timber.d("🔍 Starting scan for directory: ${directory.displayName}")
                _isScanning.value = true

                val uri = Uri.parse(directory.uri)
                val documentFile = DocumentFile.fromTreeUri(context, uri)

                if (documentFile == null || !documentFile.exists()) {
                    _error.value = "Directory not accessible: ${directory.displayName}"
                    return@launch
                }

                val discoveredFiles = scanDirectoryRecursively(documentFile, directory)

                if (discoveredFiles.isNotEmpty()) {
                    _discoveredFiles.value = _discoveredFiles.value + discoveredFiles
                    Timber.d("📊 Discovered ${discoveredFiles.size} files in ${directory.displayName}")
                } else {
                    Timber.d("📂 No new files found in ${directory.displayName}")
                }

            } catch (e: Exception) {
                _error.value = "Failed to scan directory: ${e.message}"
                Timber.e(e, "Directory scan failed for: ${directory.displayName}")
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanAllDirectories() {
        viewModelScope.launch {
            try {
                Timber.d("🔍 Starting scan for all directories")
                _isScanning.value = true
                _discoveredFiles.value = emptyList() // Clear previous results

                val directories = _directories.value
                var totalFiles = 0

                for (directory in directories) {
                    try {
                        val uri = Uri.parse(directory.uri)
                        val documentFile = DocumentFile.fromTreeUri(context, uri)

                        if (documentFile?.exists() == true) {
                            val files = scanDirectoryRecursively(documentFile, directory)
                            _discoveredFiles.value = _discoveredFiles.value + files
                            totalFiles += files.size
                            Timber.d("📊 Scanned ${directory.displayName}: ${files.size} files")
                        } else {
                            Timber.w("⚠️ Directory not accessible: ${directory.displayName}")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to scan directory: ${directory.displayName}")
                    }
                }

                Timber.d("✅ Scan complete: ${totalFiles} total files discovered")

            } catch (e: Exception) {
                _error.value = "Failed to scan directories: ${e.message}"
                Timber.e(e, "Bulk directory scan failed")
            } finally {
                _isScanning.value = false
            }
        }
    }

    // FIXED: Complete backup implementation with database integration
    fun backupAllFiles() {
        viewModelScope.launch {
            try {
                val filesToBackup = _discoveredFiles.value
                if (filesToBackup.isEmpty()) {
                    _error.value = "No files to backup. Run scan first."
                    return@launch
                }

                Timber.d("📤 Starting backup of ${filesToBackup.size} files")
                _isLoading.value = true

                var successCount = 0
                var failCount = 0
                val totalFiles = filesToBackup.size

                filesToBackup.forEachIndexed { index, discoveredFile ->
                    try {
                        // Update progress
                        withContext(Dispatchers.Main) {
                            _backupProgress.value = BackupProgress(
                                isActive = true,
                                currentFile = discoveredFile.name,
                                completedFiles = index,
                                totalFiles = totalFiles,
                                percentage = (index * 100) / totalFiles
                            )
                        }

                        Timber.d("📥 Processing file ${index + 1}/$totalFiles: ${discoveredFile.name}")

                        // FIXED: Create proper FileIndex for database
                        val fileIndex = FileIndex(
                            name = discoveredFile.name,
                            path = discoveredFile.uri,
                            size = discoveredFile.size,
                            checksum = generateChecksum(discoveredFile),
                            createdAt = Date(),
                            modifiedAt = Date(discoveredFile.lastModified),
                            shouldBackup = true,
                            isBackedUp = false,
                            backedUpAt = null,
                            fileType = getFileExtension(discoveredFile.name),
                            mimeType = discoveredFile.mimeType ?: "application/octet-stream",
                            lastAttemptedAt = Date(),
                            errorMessage = null
                        )

                        // FIXED: Insert into database using proper use case
                        val insertedId = withContext(Dispatchers.IO) {
                            try {
                                insertFileUseCase(fileIndex)
                            } catch (e: Exception) {
                                Timber.e(e, "Database insertion failed for ${discoveredFile.name}")
                                -1L
                            }
                        }

                        if (insertedId > 0) {
                            Timber.d("✅ File inserted into database with ID: $insertedId")
                            successCount++
                        } else {
                            Timber.e("❌ Database insertion failed for ${discoveredFile.name}")
                            failCount++
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Failed to process file: ${discoveredFile.name}")
                        failCount++
                    }
                }

                // Update final progress
                withContext(Dispatchers.Main) {
                    _backupProgress.value = BackupProgress(
                        isActive = false,
                        currentFile = "",
                        completedFiles = totalFiles,
                        totalFiles = totalFiles,
                        percentage = 100
                    )
                }

                Timber.i("📊 Backup complete: $successCount successful, $failCount failed")

                // Clear discovered files after processing
                _discoveredFiles.value = emptyList()

                // FIXED: Notify MainViewModel to refresh
                mainViewModelRef?.notifyDirectoryOperationComplete()

                // Clear progress after delay
                kotlinx.coroutines.delay(2000)
                _backupProgress.value = null

            } catch (e: Exception) {
                _error.value = "Backup failed: ${e.message}"
                Timber.e(e, "Backup operation failed")
                _backupProgress.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    // FIXED: Improved directory scanning with validation
    private fun scanDirectoryRecursively(
        documentFile: DocumentFile,
        directory: BackupDirectory,
        maxDepth: Int = 3,
        currentDepth: Int = 0
    ): List<DiscoveredFile> {
        val files = mutableListOf<DiscoveredFile>()

        if (currentDepth >= maxDepth) {
            return files // Prevent infinite recursion
        }

        try {
            documentFile.listFiles().forEach { file ->
                when {
                    file.isFile -> {
                        // FIXED: Validate file before adding
                        if (isValidFile(file)) {
                            files.add(
                                DiscoveredFile(
                                    name = file.name ?: "Unknown",
                                    uri = file.uri.toString(),
                                    size = file.length(),
                                    mimeType = file.type,
                                    lastModified = file.lastModified(),
                                    directoryId = directory.id,
                                    directoryName = directory.displayName,
                                    isImage = file.type?.startsWith("image/") == true,
                                    isVideo = file.type?.startsWith("video/") == true,
                                    isDocument = file.type?.let {
                                        it.contains("pdf") || it.contains("document") || it.contains("text")
                                    } == true
                                )
                            )
                        }
                    }
                    file.isDirectory && currentDepth < maxDepth - 1 -> {
                        // Recursively scan subdirectory
                        files.addAll(
                            scanDirectoryRecursively(file, directory, maxDepth, currentDepth + 1)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning directory: ${documentFile.name}")
        }

        return files
    }

    // ADD: File validation
    private fun isValidFile(documentFile: DocumentFile): Boolean {
        return try {
            documentFile.exists() &&
                    documentFile.canRead() &&
                    documentFile.length() > 0 &&
                    !documentFile.name.isNullOrEmpty()
        } catch (e: Exception) {
            Timber.w(e, "File validation failed for: ${documentFile.name}")
            false
        }
    }

    // ADD: Generate checksum for file
    private fun generateChecksum(discoveredFile: DiscoveredFile): String {
        return try {
            val input = "${discoveredFile.name}_${discoveredFile.size}_${discoveredFile.lastModified}"
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(input.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.w(e, "Checksum generation failed")
            "unknown_checksum"
        }
    }

    // ADD: Get file extension
    private fun getFileExtension(fileName: String): String {
        return try {
            fileName.substringAfterLast('.', "").lowercase()
        } catch (e: Exception) {
            "unknown"
        }
    }

    // Existing methods remain the same...
    fun removeDirectory(uri: String) {
        viewModelScope.launch {
            try {
                Timber.d("🗑️ Removing directory: $uri")
                persistentDirectoryRepository.removeDirectory(uri)
                Timber.d("✅ Directory removed successfully")
            } catch (e: Exception) {
                _error.value = "Failed to remove directory: ${e.message}"
                Timber.e(e, "Failed to remove directory")
            }
        }
    }

    fun removeDirectory(directory: BackupDirectory) {
        removeDirectory(directory.uri)
    }

    fun scanDirectory(directoryId: String) {
        viewModelScope.launch {
            try {
                val directory = _directories.value.find { it.id.toString() == directoryId }
                if (directory != null) {
                    scanDirectory(directory)
                } else {
                    _error.value = "Directory not found: $directoryId"
                    Timber.w("Directory not found for ID: $directoryId")
                }
            } catch (e: Exception) {
                _error.value = "Failed to find directory: ${e.message}"
                Timber.e(e, "Directory lookup failed for ID: $directoryId")
            }
        }
    }

    fun refreshDirectories() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Timber.d("🔄 Refreshing directories...")

                val restoredDirectories = persistentDirectoryRepository.restorePersistedDirectories()
                Timber.d("🔄 Refreshed ${restoredDirectories.size} directories")

            } catch (e: Exception) {
                _error.value = "Failed to refresh directories: ${e.message}"
                Timber.e(e, "Directory refresh failed")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    // Helper functions
    fun getDirectoryCount(): Int = _directories.value.size
    fun getDiscoveredFileCount(): Int = _discoveredFiles.value.size

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "${bytes / 1_073_741_824} GB"
            bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
            bytes >= 1_024 -> "${bytes / 1_024} KB"
            else -> "$bytes bytes"
        }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("🧹 DirectoryViewModel cleared")
    }
}

// Keep existing DiscoveredFile data class
data class DiscoveredFile(
    val name: String,
    val uri: String,
    val size: Long,
    val mimeType: String?,
    val lastModified: Long,
    val directoryId: Long,
    val directoryName: String,
    val isImage: Boolean = false,
    val isVideo: Boolean = false,
    val isDocument: Boolean = false
)

// ADD: Backup progress data class
data class BackupProgress(
    val isActive: Boolean,
    val currentFile: String,
    val completedFiles: Int,
    val totalFiles: Int,
    val percentage: Int = 0
)