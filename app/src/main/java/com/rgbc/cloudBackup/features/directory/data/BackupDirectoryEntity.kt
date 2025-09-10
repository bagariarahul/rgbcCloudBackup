package com.rgbc.cloudBackup.features.directory.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "backup_directories")
data class BackupDirectoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "file_count")
    val fileCount: Int = 0,

    @ColumnInfo(name = "last_scanned")
    val lastScanned: Long = 0L,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)