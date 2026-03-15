package com.rgbc.cloudBackup.core.service

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import com.rgbc.cloudBackup.R
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import com.rgbc.cloudBackup.core.domain.usecase.GetFilesToBackupUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadFileUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Date

/**
 * BackupWorker — runs periodic background backups via WorkManager.
 *
 * This worker:
 * 1. Queries Room for files where isBackedUp == false
 * 2. Converts content:// URIs to temp files (same logic as MainViewModel)
 * 3. Sequentially uploads each file via UploadFileUseCase
 * 4. Marks each file as backed up in the database on success
 * 5. Cleans up temp files after each upload
 *
 * Uses @HiltWorker so Hilt can inject use cases and repositories
 * via @AssistedInject. Context and WorkerParameters are @Assisted.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val getFilesToBackupUseCase: GetFilesToBackupUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val fileRepository: FileRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "periodic_backup_worker"
        private const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        Timber.i("⏰ BackupWorker started (attempt ${runAttemptCount + 1})")

        return try {
            // Show a foreground notification so the OS doesn't kill long-running uploads
            setForeground(createForegroundInfo("Checking for pending backups..."))

            // 1. Get pending files from Room
            val pendingFiles = getFilesToBackupUseCase().first()
                .filter { !it.isBackedUp && it.shouldBackup }

            if (pendingFiles.isEmpty()) {
                Timber.i("⏰ BackupWorker: No pending files. Done.")
                return Result.success()
            }

            Timber.i("⏰ BackupWorker: Found ${pendingFiles.size} files to backup")

            var successCount = 0
            var failCount = 0
            val totalCount = pendingFiles.size

            // 2. Process files sequentially (same pattern as MainViewModel.backupAllFiles)
            for ((index, fileIndex) in pendingFiles.withIndex()) {
                // Update notification progress
                setForeground(
                    createForegroundInfo("Uploading ${index + 1} of $totalCount...")
                )

                try {
                    val uploaded = uploadSingleFile(fileIndex)
                    if (uploaded) successCount++ else failCount++
                } catch (e: Exception) {
                    Timber.e(e, "⏰ BackupWorker: Exception uploading ${fileIndex.name}")
                    failCount++
                }
            }

            Timber.i("⏰ BackupWorker complete: $successCount succeeded, $failCount failed out of $totalCount")

            // 3. Return result based on outcomes
            when {
                failCount == 0 -> Result.success()
                successCount > 0 -> Result.success()  // Partial success is still success for WorkManager
                runAttemptCount < 2 -> Result.retry()  // Retry up to 3 times on full failure
                else -> Result.failure()
            }

        } catch (e: Exception) {
            Timber.e(e, "⏰ BackupWorker: Fatal error")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /**
     * Upload a single file. Returns true on success, false on failure.
     * Mirrors the logic from MainViewModel.uploadSingleFile() but without
     * any UI state management.
     */
    private suspend fun uploadSingleFile(fileIndex: FileIndex): Boolean {
        // Convert URI/path to a readable File
        val actualFile = convertUriToFile(fileIndex.path, fileIndex.name)
        if (actualFile == null || !actualFile.exists()) {
            Timber.w("⏰ Skipping inaccessible file: ${fileIndex.name}")
            return false
        }

        var uploadSucceeded = false

        try {
            uploadFileUseCase.execute(actualFile).collect { progress ->
                when (progress) {
                    is UploadProgress.Completed -> {
                        // Persist server file ID
                        val serverFileId = progress.serverId.toLongOrNull()
                        if (serverFileId != null) {
                            fileRepository.updateServerFileId(fileIndex.id, serverFileId)
                        }

                        // Mark as backed up in Room
                        fileRepository.markAsBackedUp(fileIndex.id)

                        uploadSucceeded = true
                        Timber.i("⏰ ✅ Background upload completed: ${fileIndex.name}")
                    }
                    is UploadProgress.Failed -> {
                        Timber.e("⏰ ❌ Background upload failed: ${fileIndex.name} — ${progress.error}")
                    }
                    else -> { /* Starting / Uploading — no-op in background */ }
                }
            }
        } finally {
            // Clean up temp file (only if it's in cache dir — don't delete originals)
            try {
                if (actualFile.absolutePath.startsWith(appContext.cacheDir.absolutePath)) {
                    actualFile.delete()
                    Timber.d("🗑️ Temp file cleaned up: ${actualFile.name}")
                }
            } catch (_: Exception) {}
        }

        return uploadSucceeded
    }

    /**
     * Convert a content:// URI or file path to a readable File.
     * Identical logic to MainViewModel.convertUriToFile().
     */
    private suspend fun convertUriToFile(uriString: String, fileName: String): File? =
        withContext(Dispatchers.IO) {
            try {
                when {
                    uriString.startsWith("file://") -> {
                        File(Uri.parse(uriString).path ?: uriString)
                    }
                    uriString.startsWith("content://") -> {
                        val uri = Uri.parse(uriString)
                        val documentFile = DocumentFile.fromSingleUri(appContext, uri)

                        if (documentFile?.exists() != true) {
                            Timber.w("⏰ DocumentFile does not exist: $uriString")
                            return@withContext null
                        }

                        val tempFile = File(appContext.cacheDir, "worker_temp_$fileName")
                        appContext.contentResolver.openInputStream(uri)?.use { inputStream ->
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
                Timber.e(e, "⏰ Failed to convert URI to file: $uriString")
                null
            }
        }

    /**
     * Build a ForegroundInfo notification so Android doesn't kill the worker
     * during long upload operations.
     */
    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, "backup_channel")
            .setContentTitle("Cloud Backup")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}