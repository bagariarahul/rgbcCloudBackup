package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.domain.model.BackupStats
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class GetBackupStatisticsUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(): BackupStats {
        val totalFiles     = fileRepository.countAllFiles()
        val backedUpFiles  = fileRepository.countBackedUpFiles()
        val totalSize      = fileRepository.sumAllFileSizes()
        val backedUpSize   = fileRepository.sumBackedUpFileSizes()
        val errorCount     = fileRepository.countBackupErrors()      // new
        val failedFiles    = fileRepository.countFailedBackupFiles() // new
        val pendingFiles   = totalFiles - backedUpFiles

        return BackupStats(
            totalFiles     = totalFiles,
            backedUpFiles  = backedUpFiles,
            totalSize      = totalSize,
            backedUpSize   = backedUpSize,
            pendingFiles   = pendingFiles,
            errorCount     = errorCount,
            failedFiles    = failedFiles
        )
    }
}
