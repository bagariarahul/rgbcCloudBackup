package com.rgbc.cloudBackup.core.domain.repository

data class BackupStatistics(
    val totalFiles: Int,
    val backedUpFiles: Int,
    val totalSize: Long,
    val backedUpSize: Long,
    val pendingFiles: Int
)
