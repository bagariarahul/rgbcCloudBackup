package com.rgbc.cloudBackup.features.directory.data

import androidx.room.*
import java.util.Date

@Entity(
    tableName = "backup_directories",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["uri"], unique = true)  // ADD: URI index
    ]
)
data class BackupDirectory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uri")
    val uri: String,  // ADD: URI field

    @ColumnInfo(name = "display_name")
    val displayName: String,  // ADD: Display name field

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "file_count")
    val fileCount: Int = 0,

    @ColumnInfo(name = "total_size")
    val totalSize: Long = 0,

    @ColumnInfo(name = "last_scanned")
    val lastScanned: Date? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date()
)