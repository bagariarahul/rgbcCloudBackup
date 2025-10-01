package com.rgbc.cloudBackup.core.data.database.entity

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface BackupQueueDao {

    // Query operations
    @Query("SELECT * FROM backup_queue ORDER BY priority ASC, created_at ASC")
    fun getAllQueueItems(): Flow<List<BackupQueue>>

    @Query("SELECT * FROM backup_queue WHERE status = :status ORDER BY priority ASC, created_at ASC")
    fun getItemsByStatus(status: BackupStatus): Flow<List<BackupQueue>>

    @Query("SELECT * FROM backup_queue WHERE status IN (:statuses) ORDER BY priority ASC, created_at ASC")
    suspend fun getItemsByStatuses(statuses: List<BackupStatus>): List<BackupQueue>

    @Query("SELECT * FROM backup_queue WHERE status = 'PENDING' ORDER BY priority ASC, created_at ASC LIMIT :limit")
    suspend fun getPendingItems(limit: Int = 5): List<BackupQueue>

    @Query("SELECT * FROM backup_queue WHERE status = 'FAILED' AND next_retry_at <= :currentTime ORDER BY priority ASC, created_at ASC LIMIT :limit")
    suspend fun getRetryableItems(currentTime: Date, limit: Int = 3): List<BackupQueue>

    @Query("SELECT * FROM backup_queue WHERE file_path = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): BackupQueue?

    // Insert operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: BackupQueue): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<BackupQueue>): List<Long>

    // Update operations
    @Update
    suspend fun updateQueueItem(item: BackupQueue)

    @Query("UPDATE backup_queue SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: BackupStatus, updatedAt: Date = Date())

    @Query("UPDATE backup_queue SET status = :status, error_message = :error, retry_count = retry_count + 1, next_retry_at = :nextRetry, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateFailure(id: Long, status: BackupStatus, error: String, nextRetry: Date?, updatedAt: Date = Date())

    @Query("UPDATE backup_queue SET status = 'COMPLETED', server_file_id = :serverId, completed_at = :completedAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateCompleted(id: Long, serverId: String, completedAt: Date = Date(), updatedAt: Date = Date())

    // Delete operations
    @Query("DELETE FROM backup_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM backup_queue WHERE status = 'COMPLETED' AND completed_at < :beforeDate")
    suspend fun cleanupCompletedBefore(beforeDate: Date)

    @Query("DELETE FROM backup_queue WHERE status = 'CANCELLED'")
    suspend fun cleanupCancelled()

    // Statistics
    @Query("SELECT COUNT(*) FROM backup_queue")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM backup_queue WHERE status = :status")
    suspend fun getCountByStatus(status: BackupStatus): Int

    @Query("SELECT SUM(file_size) FROM backup_queue WHERE status = 'COMPLETED'")
    suspend fun getTotalBackedUpSize(): Long?
}