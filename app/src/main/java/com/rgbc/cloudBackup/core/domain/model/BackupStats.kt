package com.rgbc.cloudBackup.core.domain.model

data class BackupStats(
    val totalFiles: Int = 0,
    val backedUpFiles: Int = 0,
    val totalSize: Long =0L,
    val backedUpSize: Long = 0L,
    val pendingFiles: Int =0,
    val errorCount: Int = 0,
    val failedFiles: Int = 0,
) {
    val backupProgress: Float
        get() = if (totalFiles > 0) backedUpFiles.toFloat() / totalFiles.toFloat() else 0f

    val sizeProgress: Float
        get() = if (totalSize > 0) backedUpSize.toFloat() / totalSize.toFloat() else 0f
}
