package com.rgbc.cloudBackup.core.data.repository

import com.rgbc.cloudBackup.core.data.database.entity.FileIndexDao
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.model.BackupStats
import com.rgbc.cloudBackup.core.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    private val fileDao: FileIndexDao
) : FileRepository {

    override fun getAllFiles(): Flow<List<FileIndex>> {
        Timber.d("Getting all files from database")
        return fileDao.getAllFiles()
    }

    override fun getFilesToBackup(): Flow<List<FileIndex>> {
        Timber.d("Getting files to backup from database")
        return fileDao.getFilesToBackup()
    }

    override fun getBackedUpFiles(): Flow<List<FileIndex>> {
        Timber.d("Getting backed up files from database")
        return fileDao.getBackedUpFiles()
    }

    override suspend fun insertFiles(files: List<FileIndex>): List<Long> {
        return try {
            Timber.i("Inserting ${files.size} files into database")
            val result = fileDao.insertFiles(files)
            Timber.i("Successfully inserted ${result.size} files")
            result
        } catch (e: Exception) {
            Timber.e(e, "Error inserting files to database")
            emptyList()
        }
    }

    override suspend fun insertFile(file: FileIndex): Long {
        return try {
            Timber.d("📥 Inserting file: ${file.name}")
            val actualId = fileDao.insertFile(file)
            Timber.d("✅ File inserted with database ID: $actualId")
            actualId
        } catch (e: Exception) {
            Timber.e(e, "❌ Error inserting file: ${file.name}")
            0L // Return 0 on error
        }
    }

    override suspend fun updateFile(file: FileIndex) {
        try {
            fileDao.updateFile(file)
            Timber.d("Updated file: ${file.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error updating file: ${file.name}")
        }
    }

    override suspend fun deleteFile(file: FileIndex) {
        try {
            fileDao.deleteFile(file)
            Timber.i("Deleted file: ${file.name}")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting file: ${file.name}")
        }
    }

    override suspend fun markAsBackedUp(fileId: Long) {
        try {
            Timber.d("🔄 Marking file ID $fileId as backed up")

            if (fileId <= 0) {
                Timber.e("❌ Invalid file ID: $fileId - cannot mark as backed up")
                return
            }

            fileDao.markAsBackedUp(fileId, Date())
            Timber.d("✅ File ID $fileId marked as backed up successfully")

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to mark file $fileId as backed up")
            throw e
        }
    }



    override suspend fun findByPath(path: String): FileIndex? {
        return try {
            Timber.d("Finding file by path: $path")
            fileDao.findByPath(path)
        } catch (e: Exception) {
            Timber.e(e, "Error finding file by path")
            null
        }
    }

    override suspend fun findByChecksum(checksum: String): FileIndex? {
        return try {
            Timber.d("Finding file by checksum: ${checksum.take(8)}...")
            fileDao.findByChecksum(checksum)
        } catch (e: Exception) {
            Timber.e(e, "Error finding file by checksum")
            null
        }
    }

    override suspend fun getBackupStats(): BackupStats {
        return try {
            Timber.d("Getting backup statistics")
            val totalFiles = fileDao.getFileCount()
            val backedUpFiles = fileDao.getBackedUpCount()
            val totalSize = fileDao.getTotalSize() ?: 0L
            val backedUpSize = fileDao.getBackedUpSize() ?: 0L
            val errorCount = fileDao.countFilesWithErrors()
            val failedFiles = fileDao.countFailedBackups()

            val stats = BackupStats(
                totalFiles = totalFiles,
                backedUpFiles = backedUpFiles,
                totalSize = totalSize,
                backedUpSize = backedUpSize,
                pendingFiles = totalFiles - backedUpFiles,
                errorCount = errorCount,
                failedFiles = failedFiles   // <--- provide this value


            )

            Timber.d("Backup statistics: $stats")
            stats
        } catch (e: Exception) {
            Timber.e(e, "Error getting backup statistics")
            BackupStats(0, 0, 0L, 0L, 0,0,0)
        }
    }

    override suspend fun clearAllFiles() {
        try {
            // Note: Room doesn't have a clearAll method in our DAO,
            // so we'll need to add it or handle it differently
            Timber.i("Clearing all files from database")
            // For now, we'll leave this as a TODO
            // fileDao.clearAllFiles()
        } catch (e: Exception) {
            Timber.e(e, "Error clearing all files")
        }
    }

    override suspend fun getFileCount(): Int {
        return try {
            fileDao.getFileCount()
        } catch (e: Exception) {
            Timber.e(e, "Error getting file count")
            0
        }
    }
    override suspend fun scanDirectory(path: String): List<FileIndex> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.walkTopDown()
            .filter { it.isFile }
            .map { file ->
                // Creation timestamp (on Android this may be the same as lastModified)
                val createdAt = Date(file.lastModified())

                // Optionally compute a checksum, e.g. MD5, or just leave blank for now
                val checksum = ""

                // Optionally detect mime-type; here we leave it null
                val mimeType: String? = null

                FileIndex(
                    name        = file.name,
                    path        = file.absolutePath,
                    size        = file.length(),
                    checksum    = checksum,
                    createdAt   = createdAt,
                    modifiedAt  = Date(file.lastModified()),
                    // shouldBackup and isBackedUp use their default values,
                    // backedUpAt    = null by default
                    fileType    = file.extension,
                    mimeType    = mimeType
                )
            }
            .toList()
    }
    override suspend fun countBackupErrors(): Int {
        return fileDao.countFilesWithErrors()       // implement here
    }

    override suspend fun countFailedBackupFiles(): Int {
        return fileDao.countFailedBackups()         // implement here
    }

    override suspend fun countAllFiles(): Int = fileDao.countAllFiles()

    override suspend fun countBackedUpFiles(): Int = fileDao.countBackedUpFiles()

    override suspend fun sumAllFileSizes(): Long = fileDao.sumAllFileSizes() ?: 0L

    override suspend fun sumBackedUpFileSizes(): Long = fileDao.sumBackedUpFileSizes() ?: 0L
    override suspend fun getBackedUpCount(): Int = fileDao.getBackedUpCount()

    override suspend fun getTotalSize(): Long = fileDao.getTotalSize() ?: 0L

    override suspend fun getBackedUpSize(): Long = fileDao.getBackedUpSize() ?: 0L

    override suspend fun countFilesWithErrors(): Int = fileDao.countFilesWithErrors()

    override suspend fun countFailedBackups(): Int = fileDao.countFailedBackups()


}
