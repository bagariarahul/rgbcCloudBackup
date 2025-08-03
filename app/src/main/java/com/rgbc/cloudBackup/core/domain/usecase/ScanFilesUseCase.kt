package com.rgbc.cloudBackup.core.domain.usecase

import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanFilesUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {

    suspend fun execute(directoryPaths: List<String>): Result<List<FileIndex>> {
        return try {
            val allScannedFiles = mutableListOf<FileIndex>()

            directoryPaths.forEach { path ->
                try {
                    val scannedFiles = fileRepository.scanDirectory(path)
                    allScannedFiles.addAll(scannedFiles)
                    Timber.d("Scanned ${scannedFiles.size} files from $path")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to scan directory: $path")
                    // Continue with other directories even if one fails
                }
            }

            // Insert new/updated files into database
            if (allScannedFiles.isNotEmpty()) {
                val insertedIds = fileRepository.insertFiles(allScannedFiles)
                Timber.d("Inserted ${insertedIds.size} files into database")
            }

            Result.success(allScannedFiles)

        } catch (e: Exception) {
            Timber.e(e, "File scanning failed")
            Result.failure(e)
        }
    }

    fun executeWithProgress(directoryPaths: List<String>): Flow<ScanProgress> = flow {
        try {
            emit(ScanProgress.Started(directoryPaths.size))

            val allScannedFiles = mutableListOf<FileIndex>()
            var completedDirectories = 0

            directoryPaths.forEach { path ->
                emit(ScanProgress.ScanningDirectory(path, completedDirectories, directoryPaths.size))

                try {
                    val scannedFiles = fileRepository.scanDirectory(path)
                    allScannedFiles.addAll(scannedFiles)
                    completedDirectories++

                    emit(ScanProgress.DirectoryCompleted(
                        path,
                        scannedFiles.size,
                        completedDirectories,
                        directoryPaths.size
                    ))

                } catch (e: Exception) {
                    emit(ScanProgress.DirectoryFailed(path, e.message ?: "Unknown error"))
                    Timber.e(e, "Failed to scan directory: $path")
                }
            }

            // Insert files into database
            if (allScannedFiles.isNotEmpty()) {
                emit(ScanProgress.SavingToDatabase(allScannedFiles.size))
                val insertedIds = fileRepository.insertFiles(allScannedFiles)
                emit(ScanProgress.DatabaseSaved(insertedIds.size))
            }

            emit(ScanProgress.Completed(allScannedFiles, allScannedFiles.size))

        } catch (e: Exception) {
            emit(ScanProgress.Failed(e.message ?: "Unknown error"))
            Timber.e(e, "File scanning with progress failed")
        }
    }
}

sealed class ScanProgress {
    data class Started(val totalDirectories: Int) : ScanProgress()
    data class ScanningDirectory(val path: String, val completed: Int, val total: Int) : ScanProgress()
    data class DirectoryCompleted(val path: String, val filesFound: Int, val completed: Int, val total: Int) : ScanProgress()
    data class DirectoryFailed(val path: String, val error: String) : ScanProgress()
    data class SavingToDatabase(val fileCount: Int) : ScanProgress()
    data class DatabaseSaved(val savedCount: Int) : ScanProgress()
    data class Completed(val files: List<FileIndex>, val totalFiles: Int) : ScanProgress()
    data class Failed(val error: String) : ScanProgress()
}
