package com.rgbc.cloudBackup.core.data.database.entity

import androidx.room.*
import java.util.Date

@Entity(
    tableName = "backup_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["priority", "created_at"]),
        Index(value = ["file_path"], unique = true)
    ]
)
data class BackupQueue(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "original_name")
    val originalName: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long,

    @ColumnInfo(name = "file_hash")
    val fileHash: String? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "status")
    val status: BackupStatus = BackupStatus.PENDING,

    @ColumnInfo(name = "priority")
    val priority: Int = 5, // 1=highest, 10=lowest

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 3,

    @ColumnInfo(name = "server_file_id")
    val serverFileId: String? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Date? = null,

    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Date? = null
)

enum class BackupStatus {
    PENDING,     // Added to queue, waiting for processing
    PROCESSING,  // Currently being uploaded
    COMPLETED,   // Successfully uploaded
    FAILED,      // Failed after max retries
    PAUSED,      // Paused by user
    CANCELLED    // Cancelled by user
}