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
        Timber.i("Starting download for file: $safeName")

        try {
            // 1. Simulate download (since we don't have real encrypted data yet)
            emit(DownloadProgress.Downloading(safeName, 0.3f))

            // Mock encrypted content for now - in real app this would come from server
            val encryptedFile = downloadFromServer()

            emit(DownloadProgress.Downloading(safeName, 0.7f))

            // 2. Decrypt the file
            emit(DownloadProgress.Decrypting(safeName))
            val outputFile = File(downloadsDir, fileIndex.name)
            val result = cryptoManager.decryptFile(encryptedFile, outputFile)

            when (result) {
                is DecryptedFileResult.Success -> {
                    val totalTime = System.currentTimeMillis() - startTime
                    auditLogger.logDownloadSuccess(safeName, totalTime)
                    Timber.i("Download and decryption completed for: $safeName")
                    emit(DownloadProgress.Completed(safeName, result.filePath))
                }
                is DecryptedFileResult.Error -> {
                    throw Exception("Decryption failed: ${result.message}")
                }
            }

        } catch (e: Exception) {
            auditLogger.logDownloadFailure(safeName, e.message ?: "Unknown error")
            Timber.e(e, "Download failed for: $safeName")
            emit(DownloadProgress.Failed(e.message ?: "Unknown error"))
        }
    }

    // Create mock encrypted data for testing
    private suspend fun downloadFromServer(): EncryptedFile {
        val response = apiService.downloadFile("anonymous", "encrypted_backup.dat")

        if (!response.isSuccessful) {
            throw Exception("Server download failed: ${response.code()}")
        }

        val serializedData = response.body()?.bytes()
            ?: throw Exception("Empty response from server")

        return EncryptedFile.fromSerialized(serializedData)
    }

}

sealed class DownloadProgress {
    data class Starting(val safeFileName: String) : DownloadProgress()
    data class Downloading(val safeFileName: String, val progress: Float) : DownloadProgress()
    data class Decrypting(val safeFileName: String) : DownloadProgress()
    data class Completed(val safeFileName: String, val outputPath: String) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}
