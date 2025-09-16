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
import kotlinx.coroutines.DelicateCoroutinesApi
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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
    private val _persistedScanResults = MutableStateFlow<ScanResults?>(null)
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

    fun scanDirectory(directoryId: String) {
        viewModelScope.launch {
            try {
                val directory = _directories.value.find { it.id == directoryId }
                if (directory != null) {
                    scanDirectory(directory) // Call the main function
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Directory not found: $directoryId"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to find directory: $directoryId")
            }
        }
    }

    fun scanDirectory(directoryEntity: BackupDirectoryEntity) {
        if (_uiState.value.isScanning) {
            Timber.w("⚠️ Scan already in progress, ignoring new request")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isScanning = true,
                    scanningDirectoryId = directoryEntity.id,
                    error = null,
                    message = null
                )

                Timber.d("📂 Scanning directory: ${directoryEntity.displayName}")

                // 🔧 FIX: Use the corrected function call
                val scanResults = scanSingleDirectoryForFiles(directoryEntity)

                // 🔧 FIX: Persist scan results
                _persistedScanResults.value = scanResults

                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanningDirectoryId = null,
                    scanResults = scanResults,
                    message = if (scanResults.newFiles.isNotEmpty()) {
                        "✅ Found ${scanResults.newFiles.size} new files to backup"
                    } else {
                        "ℹ️ No new files found"
                    }
                )

                Timber.d("✅ Scan completed: ${scanResults.newFiles.size} new files")

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanningDirectoryId = null,
                    error = "Scan failed: ${e.message}"
                )
                Timber.e(e, "❌ Directory scan failed")
            }
        }
    }

    // 🆕 ADD: Missing function to scan a single directory
    private suspend fun scanSingleDirectoryForFiles(directory: BackupDirectoryEntity): ScanResults {
        return try {
            val uri = Uri.parse(directory.uri)

            // Verify permission
            if (!hasTreeUriPermission(uri)) {
                throw Exception("No permission to access directory")
            }

            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile?.exists() != true || !documentFile.canRead()) {
                throw Exception("Directory not accessible")
            }

            val allFiles = scanDirectoryRecursively(documentFile)
            val newFiles = filterNewFiles(allFiles)

            ScanResults(
                newFiles = newFiles,
                totalSize = newFiles.sumOf { it.size },
                directoryScanned = directory.displayName,
                lastScanTime = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Timber.e(e, "Failed to scan directory: ${directory.displayName}")
            ScanResults() // Return empty results on error
        }
    }


    fun restoreScanResults() {
        val persistedResults = _persistedScanResults.value
        if (persistedResults != null) {
            Timber.d("🔄 Restoring persisted scan results: ${persistedResults.newFiles.size} files")
            _uiState.value = _uiState.value.copy(
                scanResults = persistedResults,
                message = "📂 Restored ${persistedResults.newFiles.size} files ready for backup"
            )
        }
    }

    // 🆕 ADD: Clear persisted results after successful backup
    fun clearPersistedResults() {
        _persistedScanResults.value = null
        Timber.d("🧹 Cleared persisted scan results")
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

    @OptIn(DelicateCoroutinesApi::class)
    fun backupAllNewFiles() {
        // 🔧 CRITICAL FIX: Use GlobalScope so navigation doesn't cancel backup
        GlobalScope.launch(Dispatchers.IO) {
            var successCount = 0 // 🔧 FIX: Declare variables at proper scope
            var failCount = 0    // 🔧 FIX: Declare variables at proper scope

            try {
                // Switch to Main thread for UI updates
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isScanning = false,
                        scanningDirectoryId = null,
                        error = null,
                        message = null
                    )
                }

                val filesToBackup = _uiState.value.scanResults.newFiles
                if (filesToBackup.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(error = "⚠️ No new files to backup")
                    }
                    return@launch
                }

                Timber.d("🚀 Starting backup in GlobalScope - navigation-safe")

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        backupProgress = BackupProgress(
                            isActive = true,
                            totalFiles = filesToBackup.size,
                            completedFiles = 0,
                            currentFile = ""
                        )
                    )
                }

                filesToBackup.forEachIndexed { index, file ->
                    try {
                        // Update UI on Main thread
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(
                                backupProgress = _uiState.value.backupProgress?.copy(
                                    currentFile = file.name,
                                    completedFiles = index
                                )
                            )
                        }

                        Timber.d("📤 Backing up file ${index + 1}/${filesToBackup.size}: ${file.name}")

                        val originalPath = file.path
                        val tempFile = withContext(Dispatchers.IO) {
                            createReliableTempFile(originalPath, file.name)
                        }

                        if (tempFile != null) {
                            val fileIndex = FileIndex(
                                name = file.name,
                                path = originalPath,
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

                            // 🔧 CRITICAL: Database operation on IO thread in GlobalScope
                            val actualFileId = withContext(Dispatchers.IO) {
                                try {
                                    insertFileUseCase(fileIndex)
                                } catch (e: Exception) {
                                    Timber.e(e, "Database insert failed in GlobalScope")
                                    -1L // Return invalid ID on error
                                }
                            }

                            Timber.d("📊 File inserted with actual ID: $actualFileId")

                            if (actualFileId > 0) {
                                val fileIndexWithRealId = fileIndex.copy(
                                    id = actualFileId,
                                    path = tempFile.absolutePath
                                )

                                // Upload operation
                                uploadFileUseCase.execute(fileIndexWithRealId).collect { uploadProgress ->
                                    when (uploadProgress) {
                                        is UploadProgress.Completed -> {
                                            successCount++ // 🔧 FIX: Now accessible
                                            Timber.d("✅ Successfully backed up: ${file.name} with ID: $actualFileId")
                                        }
                                        is UploadProgress.Failed -> {
                                            failCount++ // 🔧 FIX: Now accessible
                                            Timber.e("❌ Upload failed: ${uploadProgress.error}")
                                        }
                                        else -> {
                                            Timber.v("📤 Upload progress: $uploadProgress")
                                        }
                                    }
                                }
                            } else {
                                failCount++
                                Timber.e("❌ Database insert failed for: ${file.name}")
                            }

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

                // 🔧 FIX: Update UI and clear persisted results in proper scope
                withContext(Dispatchers.Main) {
                    _uiState.value = DirectoryUiState(
                        isLoading = false,
                        isScanning = false,
                        scanningDirectoryId = null,
                        scanResults = ScanResults(),
                        backupProgress = null,
                        error = null,
                        message = "✅ Backup completed: $successCount successful, $failCount failed" // 🔧 FIX: Variables now accessible
                    )

                    // 🔧 FIX: Clear persisted results after successful backup
                    clearPersistedResults()
                }

                Timber.d("🎉 GlobalScope backup complete: $successCount successful, $failCount failed")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = DirectoryUiState(
                        isLoading = false,
                        isScanning = false,
                        scanningDirectoryId = null,
                        scanResults = ScanResults(),
                        backupProgress = null,
                        error = "❌ Backup failed: ${e.message}",
                        message = null
                    )

                    // 🔧 FIX: Clear persisted results even on error
                    clearPersistedResults()
                }
                Timber.e(e, "💥 GlobalScope backup failed")
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
            Timber.d("📁 Creating temp file from URI: $uriString")
            Timber.d("🔗 Parsed URI scheme: ${uri.scheme}")

            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { input ->
                val sanitizedFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                val tempFile = File(
                    context.cacheDir,
                    "backup_${System.currentTimeMillis()}_${sanitizedFileName}"
                )

                tempFile.outputStream().use { output ->
                    val bytescopied = input.copyTo(output)
                    Timber.d("📁 Created temp file: ${tempFile.name} (${bytescopied} bytes)")
                    Timber.d("🗂️ Temp file path: ${tempFile.absolutePath}")
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    Timber.d("✅ Temp file created successfully")
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
            path = file.path,
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