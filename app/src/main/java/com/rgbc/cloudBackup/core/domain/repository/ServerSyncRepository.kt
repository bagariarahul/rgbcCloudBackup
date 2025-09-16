package com.rgbc.cloudBackup.core.domain.repository

import com.rgbc.cloudBackup.core.network.api.ServerFileInfo
import com.rgbc.cloudBackup.core.network.api.FileIntegrityInfo

interface ServerSyncRepository {
    suspend fun getServerFiles(): Result<List<ServerFileInfo>>
    suspend fun checkFileIntegrity(fileName: String): Result<FileIntegrityInfo>
    suspend fun compareWithLocal(): Result<SyncComparisonResult>
}

data class SyncComparisonResult(
    val localOnly: List<String>,
    val serverOnly: List<String>,
    val matching: List<String>,
    val sizesMismatch: List<String>,
    val corrupted: List<String>
)
