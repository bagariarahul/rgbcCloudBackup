package com.rgbc.cloudBackup.core.data.database.entity

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface FileIndexDao {

    // Existing methods...
    @Query("SELECT * FROM file_index ORDER BY modifiedAt DESC")
    fun getAllFiles(): Flow<List<FileIndex>>

    @Query("SELECT * FROM file_index WHERE shouldBackup = 1 AND isBackedUp = 0")
    fun getFilesToBackup(): Flow<List<FileIndex>>

    @Query("SELECT * FROM file_index WHERE isBackedUp = 1")
    fun getBackedUpFiles(): Flow<List<FileIndex>>

    @Query("SELECT * FROM file_index WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): FileIndex?

    @Query("SELECT * FROM file_index WHERE checksum = :checksum LIMIT 1")
    suspend fun findByChecksum(checksum: String): FileIndex?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileIndex>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileIndex): Long

    @Update
    suspend fun updateFile(file: FileIndex)

    @Delete
    suspend fun deleteFile(file: FileIndex)

    @Query("UPDATE file_index SET isBackedUp = 1, backedUpAt = :backedUpAt WHERE id = :fileId")
    suspend fun markAsBackedUp(fileId: Long, backedUpAt: Date)

    @Query("UPDATE file_index SET serverFileId = :serverFileID WHERE id = :fileId")
    suspend fun setServerFileId(fileId: Long, serverFileID: Long)


    // Count and sum operations
    @Query("SELECT COUNT(*) FROM file_index")
    suspend fun getFileCount(): Int

    @Query("SELECT COUNT(*) FROM file_index WHERE isBackedUp = 1")
    suspend fun getBackedUpCount(): Int

    @Query("SELECT SUM(size) FROM file_index")
    suspend fun getTotalSize(): Long?

    @Query("SELECT SUM(size) FROM file_index WHERE isBackedUp = 1")
    suspend fun getBackedUpSize(): Long?

    @Query("SELECT COUNT(*) FROM file_index")
    suspend fun countAllFiles(): Int

    @Query("SELECT COUNT(*) FROM file_index WHERE isBackedUp = 1")
    suspend fun countBackedUpFiles(): Int

    @Query("SELECT SUM(size) FROM file_index")
    suspend fun sumAllFileSizes(): Long?

    @Query("SELECT SUM(size) FROM file_index WHERE isBackedUp = 1")
    suspend fun sumBackedUpFileSizes(): Long?

    @Query("SELECT COUNT(*) FROM file_index WHERE errorMessage IS NOT NULL")
    suspend fun countFilesWithErrors(): Int

    @Query("SELECT COUNT(*) FROM file_index WHERE isBackedUp = 0 AND lastAttemptedAt IS NOT NULL")
    suspend fun countFailedBackups(): Int

    @Query("SELECT * FROM file_index WHERE id = :id")
    suspend fun findById(id: Long): FileIndex?

    // ADD: Missing clearAllFiles method
    @Query("DELETE FROM file_index")
    suspend fun clearAllFiles()

    // Additional utility methods for better file management
    @Query("DELETE FROM file_index WHERE isBackedUp = 1")
    suspend fun clearBackedUpFiles()

    @Query("DELETE FROM file_index WHERE isBackedUp = 0")
    suspend fun clearPendingFiles()

    @Query("DELETE FROM file_index WHERE errorMessage IS NOT NULL")
    suspend fun clearErrorFiles()

    @Query("UPDATE file_index SET shouldBackup = 0 WHERE id = :fileId")
    suspend fun markAsSkipped(fileId: Long)

    @Query("UPDATE file_index SET errorMessage = :errorMessage, lastAttemptedAt = :attemptedAt WHERE id = :fileId")
    suspend fun markAsError(fileId: Long, errorMessage: String, attemptedAt: Date)
}