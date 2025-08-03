package com.rgbc.cloudBackup.core.domain.model

data class BackupStats(
    val totalFiles: Int,
    val backedUpFiles: Int,
    val totalSize: Long,
    val backedUpSize: Long,
    val pendingFiles: Int,
    val errorCount: Int,
    val failedFiles: Int,
) {
    val backupProgress: Float
        get() = if (totalFiles > 0) backedUpFiles.toFloat() / totalFiles.toFloat() else 0f

    val sizeProgress: Float
        get() = if (totalSize > 0) backedUpSize.toFloat() / totalSize.toFloat() else 0f
}
