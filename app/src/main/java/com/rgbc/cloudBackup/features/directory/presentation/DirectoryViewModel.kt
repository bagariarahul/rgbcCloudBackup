package com.rgbc.cloudBackup.features.directory.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.data.repository.PersistentDirectoryRepository
import com.rgbc.cloudBackup.core.domain.usecase.GetAllFilesUseCase
import com.rgbc.cloudBackup.core.domain.usecase.InsertFileUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadFileUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadProgress
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class DirectoryViewModel @Inject constructor(
    private val persistentDirectoryRepository: PersistentDirectoryRepository,
    private val insertFileUseCase: InsertFileUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val getAllFilesUseCase: GetAllFilesUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DirectoryUiState())
    val uiState: StateFlow<DirectoryUiState> = _uiState.asStateFlow()

    private val _directories = MutableStateFlow<List<BackupDirectoryEntity>>(emptyList())
    val directories: StateFlow<List<BackupDirectoryEntity>> = _directories.asStateFlow()

    init {
        loadPersistedDirectories()

        viewModelScope.launch {
            persistentDirectoryRepository.getAllDirectories().collect { dirs ->
                _directories.value = dirs
                Timber.d("📂 Directories updated: ${dirs.size} directories")
            }
        }
    }

    private fun loadPersistedDirectories() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val restored = persistentDirectoryRepository.restorePersistedDirectories()
                Timber.d("🔄 Restored ${restored.size} persisted directories")

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = if (restored.isNotEmpty())
                        "✅ Restored ${restored.size} directories" else "ℹ️ No directories to restore"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "❌ Failed to restore directories: ${e.message}"
                )
            }
        }
    }

    fun addDirectory(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val result = persistentDirectoryRepository.addDirectory(uri)

                if (result.isSuccess) {
                    val directory = result.getOrThrow()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "✅ Added: ${directory.displayName}"
                    )

                    // Auto-scan the new directory
                    scanDirectory(directory)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (error.contains("already exists"))
                            "⚠️ Directory already added" else "❌ $error"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "❌ Failed to add directory: ${e.message}"
                )
            }
        }
    }

    fun removeDirectory(directory: BackupDirectoryEntity) {
        viewModelScope.launch {
            try {
                persistentDirectoryRepository.removeDirectory(directory.uri)
                _uiState.value = _uiState.value.copy(
                    message = "✅ Removed: ${directory.displayName}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "❌ Failed to remove directory: ${e.message}"
                )
            }
        }
    }

    fun scanDirectory(directory: BackupDirectoryEntity) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    scanningDirectoryId = directory.id,
                    error = null
                )

                val uri = Uri.parse(directory.uri)

                // Verify and restore permission if needed
                if (!hasTreeUriPermission(uri)) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        Timber.d("🔄 Retook permission for directory scan")
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            scanningDirectoryId = null,
                            error = "❌ Permission denied: ${directory.displayName}. Please re-add directory."
                        )
                        return@launch
                    }
                }

                val documentFile = DocumentFile.fromTreeUri(context, uri)

                if (documentFile?.exists() == true && documentFile.canRead()) {
                    val allFiles = scanDirectoryRecursively(documentFile)
                    val newFiles = filterNewFiles(allFiles)

                    _uiState.value = _uiState.value.copy(
                        scanningDirectoryId = null,
                        scanResults = ScanResults(
                            newFiles = newFiles,
                            totalSize = newFiles.sumOf { it.size },
                            directoryScanned = directory.displayName,
                            lastScanTime = System.currentTimeMillis()
                        )
                    )

                    Timber.d("📊 Scanned ${directory.displayName}: ${allFiles.size} total, ${newFiles.size} new")
                } else {
                    _uiState.value = _uiState.value.copy(
                        scanningDirectoryId = null,
                        error = "❌ Directory no longer accessible: ${directory.displayName}. Please re-add directory."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    scanningDirectoryId = null,
                    error = "❌ Scan failed: ${e.message}"
                )
            }
        }
    }

    fun scanAllDirectories() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isScanning = true,
                    scanResults = ScanResults(),
                    error = null
                )

                val allNewFiles = mutableListOf<FileToBackup>()
                val scannedDirs = mutableListOf<String>()
                val failedDirs = mutableListOf<String>()

                _directories.value.forEach { directory ->
                    try {
                        val uri = Uri.parse(directory.uri)

                        // Verify permission before scanning
                        if (!hasTreeUriPermission(uri)) {
                            try {
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                )
                            } catch (e: Exception) {
                                failedDirs.add(directory.displayName)
                                return@forEach
                            }
                        }

                        val documentFile = DocumentFile.fromTreeUri(context, uri)

                        if (documentFile?.exists() == true && documentFile.canRead()) {
                            val allFiles = scanDirectoryRecursively(documentFile)
                            val newFiles = filterNewFiles(allFiles)
                            allNewFiles.addAll(newFiles)
                            scannedDirs.add(directory.displayName)
                        } else {
                            failedDirs.add(directory.displayName)
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "❌ Failed to scan directory: ${directory.displayName}")
                        failedDirs.add(directory.displayName)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanResults = ScanResults(
                        newFiles = allNewFiles,
                        totalSize = allNewFiles.sumOf { it.size },
                        directoryScanned = "${scannedDirs.size} directories",
                        lastScanTime = System.currentTimeMillis()
                    ),
                    message = if (failedDirs.isNotEmpty())
                        "⚠️ ${failedDirs.size} directories failed to scan" else null
                )

                Timber.d("📊 Scan complete: ${allNewFiles.size} new files from ${scannedDirs.size} directories")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    error = "❌ Scan failed: ${e.message}"
                )
            }
        }
    }

    fun backupAllNewFiles() {
        viewModelScope.launch {
            try {
                val filesToBackup = _uiState.value.scanResults.newFiles
                if (filesToBackup.isEmpty()) {
                    _uiState.value = _uiState.value.copy(error = "⚠️ No new files to backup")
                    return@launch
                }

                Timber.d("🚀 Starting backup of ${filesToBackup.size} files")

                _uiState.value = _uiState.value.copy(
                    backupProgress = BackupProgress(
                        isActive = true,
                        totalFiles = filesToBackup.size,
                        completedFiles = 0,
                        currentFile = ""
                    )
                )

                var successCount = 0
                var failCount = 0

                filesToBackup.forEachIndexed { index, file ->
                    try {
                        _uiState.value = _uiState.value.copy(
                            backupProgress = _uiState.value.backupProgress?.copy(
                                currentFile = file.name,
                                completedFiles = index
                            )
                        )

                        Timber.d("📤 Backing up file ${index + 1}/${filesToBackup.size}: ${file.name}")

                        val tempFile = createReliableTempFile(file.path, file.name)

                        if (tempFile != null) {
                            val fileIndex = createFileIndex(file, tempFile.absolutePath)
                            insertFileUseCase(fileIndex)

                            // Upload with progress tracking
                            uploadFileUseCase.execute(fileIndex).collect { uploadProgress ->
                                when (uploadProgress) {
                                    is UploadProgress.Completed -> {
                                        successCount++
                                        Timber.d("✅ Successfully backed up: ${file.name}")
                                    }
                                    is UploadProgress.Failed -> {
                                        failCount++
                                        Timber.e("❌ Upload failed: ${uploadProgress.error}")
                                    }
                                    else -> { }
                                }
                            }

                            // Clean up temp file
                            try {
                                tempFile.delete()
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to delete temp file")
                            }
                        } else {
                            failCount++
                            Timber.e("❌ Failed to create temp file for: ${file.name}")
                        }
                    } catch (e: Exception) {
                        failCount++
                        Timber.e(e, "❌ Failed to backup file: ${file.name}")
                    }
                }

                // Complete backup
                _uiState.value = _uiState.value.copy(
                    backupProgress = BackupProgress(
                        isActive = false,
                        totalFiles = filesToBackup.size,
                        completedFiles = filesToBackup.size,
                        currentFile = ""
                    ),
                    scanResults = _uiState.value.scanResults.copy(newFiles = emptyList()),
                    message = "✅ Backup completed: $successCount successful, $failCount failed"
                )

                Timber.d("🎉 Backup complete: $successCount successful, $failCount failed")

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    backupProgress = BackupProgress(isActive = false),
                    error = "❌ Backup failed: ${e.message}"
                )
            }
        }
    }

    // FIXED: Check tree URI permission instead of individual file URIs
    private fun hasTreeUriPermission(uri: Uri): Boolean {
        return try {
            val persistedUris = context.contentResolver.persistedUriPermissions
            persistedUris.any { it.uri == uri && it.isReadPermission }
        } catch (e: Exception) {
            false
        }
    }

    // FIXED: Don't check individual file permissions - just try to access
    private fun canAccessFileUri(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                // If we can open it, we have access
                true
            } ?: false
        } catch (e: Exception) {
            Timber.w(e, "⚠️ Cannot access file URI: $uri")
            false
        }
    }

    private suspend fun scanDirectoryRecursively(
        documentFile: DocumentFile,
        maxDepth: Int = 3,
        currentDepth: Int = 0
    ): List<FileToBackup> {
        if (currentDepth > maxDepth) return emptyList()

        val files = mutableListOf<FileToBackup>()

        try {
            documentFile.listFiles().forEach { file ->
                when {
                    file.isFile && file.length() > 0 -> {
                        files.add(FileToBackup(
                            name = file.name ?: "unknown_${System.currentTimeMillis()}",
                            path = file.uri.toString(),
                            size = file.length(),
                            lastModified = file.lastModified(),
                            mimeType = file.type
                        ))
                    }
                    file.isDirectory -> {
                        files.addAll(scanDirectoryRecursively(file, maxDepth, currentDepth + 1))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error scanning directory: ${documentFile.name}")
        }

        return files
    }

    private suspend fun filterNewFiles(files: List<FileToBackup>): List<FileToBackup> {
        return try {
            val backedUpFiles = getAllFilesUseCase().first()
            val backedUpNames = backedUpFiles.map { it.name }.toSet()
            files.filterNot { backedUpNames.contains(it.name) }
        } catch (e: Exception) {
            Timber.e(e, "❌ Error filtering files")
            files
        }
    }

    // FIXED: Simplified temp file creation without permission check
    private fun createReliableTempFile(uriString: String, fileName: String): File? {
        return try {
            val uri = Uri.parse(uriString)

            Timber.d("📁 Creating temp file for: $fileName")
            Timber.d("🔗 URI: $uriString")

            val inputStream = context.contentResolver.openInputStream(uri)

            inputStream?.use { input ->
                // Create temp file with original name for easier debugging
                val sanitizedFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val tempFile = File(
                    context.cacheDir,
                    "backup_${System.currentTimeMillis()}_${sanitizedFileName}"
                )

                tempFile.outputStream().use { output ->
                    val bytescopied = input.copyTo(output)
                    Timber.d("📁 Created temp file: ${tempFile.name} (${bytescopied} bytes)")
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    Timber.d("✅ Temp file created successfully: ${tempFile.absolutePath}")
                    tempFile
                } else {
                    Timber.w("⚠️ Temp file created but empty or missing")
                    null
                }
            } ?: run {
                Timber.e("❌ Could not open input stream for URI: $uriString")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to create temp file for: $fileName from URI: $uriString")
            null
        }
    }

    private fun createFileIndex(file: FileToBackup, tempPath: String): FileIndex {
        return FileIndex(
            name = file.name,
            path = tempPath,
            size = file.size,
            checksum = generateChecksum(file),
            createdAt = Date(),
            modifiedAt = Date(file.lastModified),
            shouldBackup = true,
            isBackedUp = false,
            backedUpAt = null,
            fileType = getFileExtension(file.name),
            mimeType = file.mimeType ?: "application/octet-stream",
            lastAttemptedAt = Date(),
            errorMessage = null
        )
    }

    private fun generateChecksum(file: FileToBackup): String {
        return "${file.name.hashCode()}_${file.size}_${file.lastModified}".take(32)
    }

    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

// UI State classes remain the same
data class DirectoryUiState(
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
    val scanningDirectoryId: String? = null,
    val scanResults: ScanResults = ScanResults(),
    val backupProgress: BackupProgress? = null,
    val error: String? = null,
    val message: String? = null
)

data class FileToBackup(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String? = null
)

data class ScanResults(
    val newFiles: List<FileToBackup> = emptyList(),
    val totalSize: Long = 0,
    val directoryScanned: String = "",
    val lastScanTime: Long = 0
)

data class BackupProgress(
    val isActive: Boolean = false,
    val currentFile: String = "",
    val completedFiles: Int = 0,
    val totalFiles: Int = 0
) {
    val progressPercent: Float
        get() = if (totalFiles > 0) completedFiles.toFloat() / totalFiles.toFloat() else 0f
}