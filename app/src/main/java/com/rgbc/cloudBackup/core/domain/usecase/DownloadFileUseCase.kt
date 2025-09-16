package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import com.rgbc.cloudBackup.core.security.CryptoManagerImpl
import com.rgbc.cloudBackup.core.security.DecryptedFileResult
import com.rgbc.cloudBackup.core.security.EncryptedFile
import com.rgbc.cloudBackup.core.security.SecurityAuditLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.rgbc.cloudBackup.core.security.EncryptedData
import retrofit2.http.GET


@Singleton
class DownloadFileUseCase @Inject constructor(
    private val apiService: BackupApiService,
    private val auditLogger: SecurityAuditLogger,
    private val cryptoManager: CryptoManagerImpl
) {

    private fun getSafeDisplayName(fileName: String): String {
        return "File_${fileName.hashCode().toString().takeLast(8)}"
    }

    fun execute(fileIndex: FileIndex, downloadsDir: File): Flow<DownloadProgress> = flow {
        val startTime = System.currentTimeMillis()
        val safeName = getSafeDisplayName(fileIndex.name)
        emit(DownloadProgress.Starting(safeName))
        auditLogger.logDownloadStart(safeName, fileIndex.size)
        Timber.i("🔍 Starting download for file: $safeName (ID: ${fileIndex.id})")

        try {
            // 1. Download file using the file ID from server
            emit(DownloadProgress.Downloading(safeName, 0.1f))
            Timber.d("📥 Step 1: Downloading file from server using file ID...")

            // FIXED: Use actual file ID instead of hardcoded filename
            val response = if (fileIndex.id.isNotEmpty()) {
                // Use new endpoint with file ID
                apiService.downloadFileById(fileIndex.id.toString())
            } else {
                // Fallback to legacy endpoint
                apiService.downloadFile("anonymous", "encrypted_backup.dat")
            }

            if (!response.isSuccessful) {
                throw Exception("Server download failed: ${response.code()}")
            }

            val fileData = response.body()?.bytes()
                ?: throw Exception("Empty response from server")

            Timber.d("📊 Downloaded file data: ${fileData.size} bytes")
            emit(DownloadProgress.Downloading(safeName, 0.5f))

            // 2. Check if this is an encrypted file or regular file
            val isEncryptedFile = isEncryptedFileFormat(fileData)

            if (isEncryptedFile) {
                Timber.d("🔍 Detected encrypted file format, attempting decryption...")
                handleEncryptedFile(fileData, fileIndex, downloadsDir, safeName)
            } else {
                Timber.d("🔍 Detected regular file, saving directly...")
                handleRegularFile(fileData, fileIndex, downloadsDir, safeName)
            }

            emit(DownloadProgress.Downloading(safeName, 0.9f))

            val totalTime = System.currentTimeMillis() - startTime
            auditLogger.logDownloadSuccess(safeName, totalTime)

            val outputPath = File(downloadsDir, fileIndex.name).absolutePath
            emit(DownloadProgress.Completed(safeName, outputPath))

        } catch (e: Exception) {
            auditLogger.logDownloadFailure(safeName, e.message ?: "Unknown error")
            Timber.e(e, "❌ Download failed for: $safeName")
            emit(DownloadProgress.Failed("Download failed: ${e.message}"))
        }
    }

    private fun Long.isNotEmpty(): Boolean {
        if ( this.toString().isEmpty())
        return false;

        return true;
    }

    private fun isEncryptedFileFormat(data: ByteArray): Boolean {
        // Check if first 4 bytes contain a reasonable header size
        if (data.size < 4) return false

        val headerSize = ((data[0].toInt() and 0xFF) shl 24) or
                ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or
                (data[3].toInt() and 0xFF)

        // Header size should be reasonable (between 50 and 1000 bytes typically)
        return headerSize in 50..1000 && headerSize < data.size - 4
    }

    private suspend fun handleEncryptedFile(
        serializedData: ByteArray,
        fileIndex: FileIndex,
        downloadsDir: File,
        safeName: String
    ) {
        // Original encrypted file handling logic
        Timber.d("🔍 Analyzing encrypted data structure...")

        if (serializedData.size < 4) {
            throw Exception("Data too small: ${serializedData.size} bytes")
        }

        val headerSize = ((serializedData[0].toInt() and 0xFF) shl 24) or
                ((serializedData[1].toInt() and 0xFF) shl 16) or
                ((serializedData[2].toInt() and 0xFF) shl 8) or
                (serializedData[3].toInt() and 0xFF)

        Timber.d("🔍 Header size: $headerSize bytes")

        if (headerSize <= 0 || headerSize > serializedData.size - 4) {
            throw Exception("Invalid header size: $headerSize (total: ${serializedData.size})")
        }

        // Parse metadata
        val metadataBytes = serializedData.sliceArray(4 until 4 + headerSize)
        val metadataJson = String(metadataBytes, Charsets.UTF_8)

        Timber.d("🔍 Metadata JSON: $metadataJson")

        val gson = com.google.gson.Gson()
        val metadata = gson.fromJson(metadataJson, Map::class.java) as Map<String, Any>

        val originalName = metadata["originalFileName"] as? String ?: "unknown"
        val originalSize = (metadata["originalSize"] as? Double)?.toLong() ?: 0L
        val algorithm = metadata["algorithm"] as? String ?: "unknown"
        val ivLength = (metadata["ivLength"] as? Double)?.toInt() ?: 0
        val dataLength = (metadata["dataLength"] as? Double)?.toInt() ?: 0

        // Extract encryption components
        var offset = 4 + headerSize
        val iv = serializedData.sliceArray(offset until offset + ivLength)
        offset += ivLength
        val ciphertext = serializedData.sliceArray(offset until offset + dataLength)

        // Create and decrypt
        val encryptedFile = com.rgbc.cloudBackup.core.security.EncryptedFile(
            originalFileName = originalName,
            originalSize = originalSize,
            encryptedData = com.rgbc.cloudBackup.core.security.EncryptedData(ciphertext, iv),
            encryptionAlgorithm = algorithm,
            encryptionTimestamp = System.currentTimeMillis()
        )

        val outputFile = File(downloadsDir, fileIndex.name)
        val result = cryptoManager.decryptFile(encryptedFile, outputFile)

        when (result) {
            is DecryptedFileResult.Success -> {
                Timber.d("✅ Decryption successful: ${result.filePath}")
            }
            is DecryptedFileResult.Error -> {
                throw Exception("Decryption failed: ${result.message}")
            }
        }
    }

    private suspend fun handleRegularFile(
        fileData: ByteArray,
        fileIndex: FileIndex,
        downloadsDir: File,
        safeName: String
    ) {
        // Save regular file directly
        val outputFile = File(downloadsDir, fileIndex.name)

        withContext(Dispatchers.IO) {
            outputFile.writeBytes(fileData)
        }

        Timber.d("✅ Regular file saved: ${outputFile.absolutePath}")
        Timber.d("📏 File size: ${outputFile.length()} bytes")
    }
}

sealed class DownloadProgress {
    data class Starting(val safeFileName: String) : DownloadProgress()
    data class Downloading(val safeFileName: String, val progress: Float) : DownloadProgress()
    data class Decrypting(val safeFileName: String) : DownloadProgress()
    data class Completed(val safeFileName: String, val outputPath: String) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}



// ADD THESE DATA CLASSES:

data class FileListResponse(
    val success: Boolean,
    val files: List<RemoteFile>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

data class RemoteFile(
    val id: String,
    val name: String,
    val size: Long,
    val type: String,
    val uploadedAt: String,
    val hash: String?
)
