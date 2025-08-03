// File: MainUiState.kt
package com.rgbc.cloudBackup.features.main.presentation

import com.rgbc.cloudBackup.core.domain.model.BackupStats
import com.rgbc.cloudBackup.core.domain.usecase.ScanProgress

data class MainUiState(
    val isScanning: Boolean = false,
    val scanProgress: ScanProgress? = null,
    val backupStatistics: BackupStats = BackupStats(0, 0, 0, 0, 0,0,0),
    val errorMessage: String? = null
)
