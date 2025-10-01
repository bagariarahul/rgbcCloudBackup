package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@ViewModelScoped
class DownloadFileUseCase @Inject constructor(
    private val backupApiService: BackupApiService
) {
    suspend fun execute(fileIndex: FileIndex, downloadDir: File): Flow<DownloadProgress> = flow {
        try {
            val safeFileName = getSafeFileName(fileIndex.name)

            Timber.d("📥 Starting download for file: ${fileIndex.name} (ID: ${fileIndex.id})")
            emit(DownloadProgress.Starting(safeFileName))

            // Use fileIndex.id as server file ID for now
            // TODO: Update to use fileIndex.serverId when database is updated
            val serverId = fileIndex.id.toString()

            Timber.d("📥 Calling server API for file ID: $serverId")
            val response = backupApiService.downloadFileById(serverId)

            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                val outputFile = File(downloadDir, safeFileName)

                Timber.d("📥 Server response successful, saving to: ${outputFile.absolutePath}")
                emit(DownloadProgress.Downloading(safeFileName, 0.3f))

                try {
                    // Write response body to file
                    responseBody.byteStream().use { inputStream ->
                        outputFile.outputStream().use { outputStream ->
                            val buffer = ByteArray(8192)
                            var totalBytes = 0L
                            var bytes = inputStream.read(buffer)

                            while (bytes != -1) {
                                outputStream.write(buffer, 0, bytes)
                                totalBytes += bytes

                                // Update progress periodically
                                if (totalBytes % (8192 * 10) == 0L) {
                                    emit(DownloadProgress.Downloading(safeFileName, 0.7f))
                                }

                                bytes = inputStream.read(buffer)
                            }

                            outputStream.flush()
                        }
                    }

                    // Verify file was created and has content
                    if (outputFile.exists() && outputFile.length() > 0) {
                        Timber.i("📥 ✅ Download completed successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
                        emit(DownloadProgress.Completed(safeFileName, outputFile.absolutePath))
                    } else {
                        throw Exception("Downloaded file is empty or not created")
                    }

                } catch (e: Exception) {
                    Timber.e(e, "📥 ❌ Failed to write downloaded file")
                    // Clean up partial file
                    try {
                        if (outputFile.exists()) outputFile.delete()
                    } catch (deleteException: Exception) {
                        Timber.w(deleteException, "Failed to delete partial download file")
                    }
                    emit(DownloadProgress.Failed("Failed to save file: ${e.message}"))
                }

            } else {
                val errorMsg = "Server error: ${response.code()} - ${response.message()}"
                Timber.e("📥 ❌ Download failed: $errorMsg")
                emit(DownloadProgress.Failed(errorMsg))
            }

        } catch (e: Exception) {
            Timber.e(e, "📥 ❌ Download failed with exception")
            emit(DownloadProgress.Failed("Download error: ${e.message}"))
        }
    }

    private fun getSafeFileName(originalName: String): String {
        return try {
            if (originalName.isBlank()) {
                "downloaded_file_${System.currentTimeMillis()}"
            } else {
                // Remove any unsafe characters for filename
                originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            }
        } catch (e: Exception) {
            "downloaded_file_${System.currentTimeMillis()}"
        }
    }
}

// Download progress tracking
sealed class DownloadProgress {
    data class Starting(val safeFileName: String) : DownloadProgress()
    data class Downloading(val safeFileName: String, val progress: Float) : DownloadProgress()
    data class Decrypting(val safeFileName: String) : DownloadProgress()
    data class Completed(val safeFileName: String, val outputPath: String) : DownloadProgress()
    data class Failed(val error: String) : DownloadProgress()
}