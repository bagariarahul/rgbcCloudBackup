// File: MainUiState.kt
package com.rgbc.cloudBackup.features.main.presentation

import com.rgbc.cloudBackup.core.domain.model.BackupStats
import com.rgbc.cloudBackup.core.domain.usecase.ScanProgress

data class MainUiState(
    val isScanning: Boolean = false,
    val isLoading: Boolean = false, // 🆕 ADD: General loading state
    val scanProgress: ScanProgress? = null,
    val backupStatistics: BackupStats = BackupStats(0, 0, 0, 0, 0, 0, 0),
    val errorMessage: String? = null,
    val successMessage: String? = null, // 🆕 ADD: Success message
    val fileActionInProgress: FileActionProgress? = null, // 🆕 ADD: Individual file progress
    val lastRefreshTime: Long = 0L
)


data class FileActionProgress(
    val fileName: String,
    val action: String, // "Upload", "Download", "Delete"
    val progress: Float = 0f,
    val isActive: Boolean = true
)