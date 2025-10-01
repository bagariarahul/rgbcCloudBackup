package com.rgbc.cloudBackup.core.domain.repository

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.model.BackupStats
import kotlinx.coroutines.flow.Flow

interface FileRepository {

    fun getAllFiles(): Flow<List<FileIndex>>

    fun getFilesToBackup(): Flow<List<FileIndex>>

    fun getBackedUpFiles(): Flow<List<FileIndex>>

    suspend fun insertFiles(files: List<FileIndex>): List<Long>

    suspend fun insertFile(file: FileIndex): Long

    suspend fun updateFile(file: FileIndex)

    suspend fun deleteFile(file: FileIndex)

    suspend fun markAsBackedUp(fileId: Long)

    suspend fun findByPath(path: String): FileIndex?

    suspend fun findByChecksum(checksum: String): FileIndex?

    suspend fun getBackupStats(): BackupStats

    suspend fun clearAllFiles()

    suspend fun getFileCount(): Int

    suspend fun scanDirectory(path: String): List<FileIndex>

    suspend fun countBackupErrors(): Int

    suspend fun countFailedBackupFiles(): Int


    suspend fun countAllFiles(): Int
    suspend fun countBackedUpFiles(): Int
    suspend fun sumAllFileSizes(): Long
    suspend fun sumBackedUpFileSizes(): Long


    suspend fun getBackedUpCount(): Int

    suspend fun getTotalSize(): Long
    suspend fun getBackedUpSize(): Long
    suspend fun countFilesWithErrors(): Int
    suspend fun countFailedBackups(): Int

    suspend fun updateServerFileId(fileId: Long, serverFileId: Long)
}
