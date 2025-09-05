package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import com.rgbc.cloudBackup.core.security.CryptoManagerImpl
import com.rgbc.cloudBackup.core.security.EncryptedFile
import com.rgbc.cloudBackup.core.security.EncryptedFileResult
import com.rgbc.cloudBackup.core.security.SecurityAuditLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadFileUseCase @Inject constructor(
    private val apiService: BackupApiService,
    private val fileRepository: FileRepository,
    private val auditLogger: SecurityAuditLogger,
    private val cryptoManager: CryptoManagerImpl

) {

    private fun getSafeDisplayName(fileName: String): String {
        return "File_${fileName.hashCode().toString().takeLast(8)}"
    }

    fun execute(fileIndex: FileIndex): Flow<UploadProgress> = flow {
        val startTime = System.currentTimeMillis()
        val safeDisplayName = getSafeDisplayName(fileIndex.name)

        try {
            emit(UploadProgress.Starting(safeDisplayName))
            auditLogger.logUploadStart(safeDisplayName, fileIndex.size)
            Timber.i("Starting upload for file: $safeDisplayName")

            val file = File(fileIndex.path)
            if (!file.exists()) {
                throw Exception("File not found")
            }

            // Step 1: Encrypt the file
            emit(UploadProgress.Encrypting(safeDisplayName, 0.2f))
            val encryptionResult = cryptoManager.encryptFile(file)

            when (encryptionResult) {
                is EncryptedFileResult.Error -> {
                    throw Exception("Encryption failed: ${encryptionResult.message}")
                }
                is EncryptedFileResult.Success -> {
                    val encryptedFile = encryptionResult.encryptedFile

                    // Step 2: Create temporary encrypted file for upload
                    emit(UploadProgress.Uploading(safeDisplayName, 0.5f))
                    val tempEncryptedFile = File(file.parent, "${file.name}.encrypted")
                    tempEncryptedFile.writeBytes(serializeEncryptedFile(encryptedFile))

                    // Step 3: Upload encrypted file
                    val requestFile = tempEncryptedFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", "encrypted_backup.dat", requestFile)

                    val metadata = """{"originalSize":${encryptedFile.originalSize},"encrypted":true,"algorithm":"${encryptedFile.encryptionAlgorithm}"}"""

                    emit(UploadProgress.Uploading(safeDisplayName, 0.8f))
                    val response = apiService.uploadFile(body, metadata)

                    // Clean up temporary file
                    tempEncryptedFile.delete()

                    if (response.isSuccessful) {
                        fileRepository.markAsBackedUp(fileIndex.id)
                        val uploadDuration = System.currentTimeMillis() - startTime
                        auditLogger.logUploadSuccess(safeDisplayName, uploadDuration)
                        emit(UploadProgress.Completed(safeDisplayName))
                    } else {
                        throw Exception("Upload failed: ${response.code()}")
                    }
                }
            }

        } catch (e: Exception) {
            auditLogger.logUploadFailure(safeDisplayName, e.message ?: "Unknown error")
            emit(UploadProgress.Failed(e.message ?: "Unknown error"))
        }
    }


    private fun serializeEncryptedFile(encryptedFile: EncryptedFile): ByteArray {
        // Simple serialization - in production use proper serialization (JSON/protobuf)
        val header = """
            ENCRYPTED_FILE_v1
            original_name:${encryptedFile.originalFileName}
            original_size:${encryptedFile.originalSize}
            algorithm:${encryptedFile.encryptionAlgorithm}
            timestamp:${encryptedFile.encryptionTimestamp}
            iv_length:${encryptedFile.encryptedData.iv.size}
            data_length:${encryptedFile.encryptedData.ciphertext.size}
            ---
        """.trimIndent().toByteArray()

        return header + encryptedFile.encryptedData.iv + encryptedFile.encryptedData.ciphertext
    }
}
sealed class UploadProgress {
    data class Starting(val safeFileName: String) : UploadProgress()
    data class Encrypting(val safeFileName: String, val progress: Float) : UploadProgress()
    data class Uploading(val safeFileName: String, val progress: Float) : UploadProgress()
    data class Completed(val safeFileName: String) : UploadProgress()
    data class Failed(val error: String) : UploadProgress()
}
