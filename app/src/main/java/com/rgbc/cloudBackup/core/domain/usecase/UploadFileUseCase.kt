package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.network.api.BackupApiService
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
    private val auditLogger: SecurityAuditLogger
) {

    private fun getSafeDisplayName(fileName: String): String {
        return "File_${fileName.hashCode().toString().takeLast(8)}"
    }

    fun execute(file: File): Flow<UploadProgress> = flow {
        val startTime = System.currentTimeMillis()
        val safeName = getSafeDisplayName(file.name)

        emit(UploadProgress.Starting(safeName))
        auditLogger.logUploadStart(safeName, file.length())
        Timber.i("📤 Starting upload for file: $safeName")

        try {
            emit(UploadProgress.Uploading(safeName, 0.1f))

            // Create multipart body
            val requestFile = file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            Timber.d("📤 Sending file to server...")
            val response = apiService.uploadFile(body)

            if (response.isSuccessful && response.body() != null) {
                val uploadResponse = response.body()!!
                val totalTime = System.currentTimeMillis() - startTime

                auditLogger.logUploadSuccess(safeName, totalTime)
                Timber.i("📤 Upload successful: ${uploadResponse.message}")

                emit(UploadProgress.Completed(
                    safeName,
                    uploadResponse.file.id.toString(),
                    uploadResponse.file.originalName
                ))
            } else {
                val error = "Upload failed: ${response.code()}"
                auditLogger.logUploadFailure(safeName, error)
                Timber.e("📤 Upload failed: $error")
                emit(UploadProgress.Failed(error))
            }

        } catch (e: Exception) {
            val error = "Upload failed: ${e.message}"
            auditLogger.logUploadFailure(safeName, error)
            Timber.e(e, "📤 Upload exception for: $safeName")
            emit(UploadProgress.Failed(error))
        }
    }
}

sealed class UploadProgress {
    data class Starting(val safeFileName: String) : UploadProgress()
    data class Uploading(val safeFileName: String, val progress: Float) : UploadProgress()
    data class Completed(
        val safeFileName: String,
        val serverId: String,
        val serverFileName: String
    ) : UploadProgress()
    data class Failed(val error: String) : UploadProgress()
}