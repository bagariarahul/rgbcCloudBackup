package com.rgbc.cloudBackup.core.domain.repository

import com.rgbc.cloudBackup.core.data.database.entity.BackupQueue
import com.rgbc.cloudBackup.core.data.database.entity.BackupQueueDao
import com.rgbc.cloudBackup.core.data.database.entity.BackupStatus
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupQueueRepository @Inject constructor(
    private val backupQueueDao: BackupQueueDao
) {

    fun getAllQueueItems(): Flow<List<BackupQueue>> {
        return backupQueueDao.getAllQueueItems()
    }

    fun getItemsByStatus(status: BackupStatus): Flow<List<BackupQueue>> {
        return backupQueueDao.getItemsByStatus(status)
    }

    suspend fun addFileToQueue(
        filePath: String,
        priority: Int = 5,
        maxRetries: Int = 3
    ): Result<Long> {
        return try {
            val file = File(filePath)

            if (!file.exists()) {
                return Result.failure(Exception("File does not exist: $filePath"))
            }

            if (!file.canRead()) {
                return Result.failure(Exception("Cannot read file: $filePath"))
            }

            // Check if already in queue
            val existing = backupQueueDao.getByFilePath(filePath)
            if (existing != null && existing.status != BackupStatus.FAILED && existing.status != BackupStatus.CANCELLED) {
                Timber.d("📋 File already in queue: $filePath")
                return Result.success(existing.id)
            }

            // Calculate file hash for deduplication
            val fileHash = calculateFileHash(file)

            // Create queue item
            val queueItem = BackupQueue(
                filePath = filePath,
                originalName = file.name,
                fileSize = file.length(),
                fileHash = fileHash,
                mimeType = getMimeType(file),
                status = BackupStatus.PENDING,
                priority = priority,
                maxRetries = maxRetries
            )

            val queueId = backupQueueDao.insertQueueItem(queueItem)
            Timber.i("📋 Added file to backup queue: ${file.name} (ID: $queueId)")

            Result.success(queueId)

        } catch (e: Exception) {
            Timber.e(e, "📋 Failed to add file to queue: $filePath")
            Result.failure(e)
        }
    }

    suspend fun addFilesToQueue(
        filePaths: List<String>,
        priority: Int = 5
    ): Result<List<Long>> {
        return try {
            val queueItems = filePaths.mapNotNull { filePath ->
                val file = File(filePath)
                if (file.exists() && file.canRead()) {
                    BackupQueue(
                        filePath = filePath,
                        originalName = file.name,
                        fileSize = file.length(),
                        fileHash = calculateFileHash(file),
                        mimeType = getMimeType(file),
                        status = BackupStatus.PENDING,
                        priority = priority
                    )
                } else {
                    Timber.w("📋 Skipping inaccessible file: $filePath")
                    null
                }
            }

            val queueIds = backupQueueDao.insertQueueItems(queueItems)
            Timber.i("📋 Added ${queueIds.size} files to backup queue")

            Result.success(queueIds)

        } catch (e: Exception) {
            Timber.e(e, "📋 Failed to add files to queue")
            Result.failure(e)
        }
    }

    suspend fun updateItemStatus(id: Long, status: BackupStatus) {
        backupQueueDao.updateStatus(id, status)
    }

    suspend fun cancelItem(id: Long) {
        backupQueueDao.updateStatus(id, BackupStatus.CANCELLED)
        Timber.d("📋 Cancelled queue item: $id")
    }

    suspend fun retryItem(id: Long) {
        backupQueueDao.updateStatus(id, BackupStatus.PENDING)
        Timber.d("📋 Retry queue item: $id")
    }

    suspend fun removeItem(id: Long) {
        backupQueueDao.deleteById(id)
        Timber.d("📋 Removed queue item: $id")
    }

    suspend fun getQueueStatistics(): QueueStatistics {
        return QueueStatistics(
            total = backupQueueDao.getTotalCount(),
            pending = backupQueueDao.getCountByStatus(BackupStatus.PENDING),
            processing = backupQueueDao.getCountByStatus(BackupStatus.PROCESSING),
            completed = backupQueueDao.getCountByStatus(BackupStatus.COMPLETED),
            failed = backupQueueDao.getCountByStatus(BackupStatus.FAILED),
            totalBackedUpSize = backupQueueDao.getTotalBackedUpSize() ?: 0L
        )
    }

    private fun calculateFileHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = file.readBytes()
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate file hash")
            null
        }
    }

    private fun getMimeType(file: File): String? {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }
}

data class QueueStatistics(
    val total: Int,
    val pending: Int,
    val processing: Int,
    val completed: Int,
    val failed: Int,
    val totalBackedUpSize: Long
)