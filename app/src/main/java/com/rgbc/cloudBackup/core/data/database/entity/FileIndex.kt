package com.rgbc.cloudBackup.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "file_index",
    indices = [
        Index(value = ["checksum"], unique = true),
        Index(value = ["path"], unique = true),
        Index(value = ["shouldBackup"]),
        Index(value = ["isBackedUp"])
    ]
)
data class FileIndex(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "size")
    val size: Long,

    @ColumnInfo(name = "checksum")
    val checksum: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Date,

    @ColumnInfo(name = "modifiedAt")
    val modifiedAt: Date,

    @ColumnInfo(name = "shouldBackup")
    val shouldBackup: Boolean = true,

    @ColumnInfo(name = "isBackedUp")
    val isBackedUp: Boolean = false,

    @ColumnInfo(name = "backedUpAt")
    val backedUpAt: Date? = null,

    @ColumnInfo(name = "fileType")
    val fileType: String,

    @ColumnInfo(name = "mimeType")
    val mimeType: String? = null,

    // New fields for error tracking:
    @ColumnInfo(name = "lastAttemptedAt")
    val lastAttemptedAt: Date? = null,

    @ColumnInfo(name = "errorMessage")
    val errorMessage: String? = null,

    @ColumnInfo(name = "serverFileId")
    val serverFileId: Long? = null,
)
