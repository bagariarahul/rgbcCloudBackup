package com.rgbc.cloudBackup.core.data.database.entity

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface FileIndexDao {

    @Query("SELECT * FROM file_index ORDER BY modifiedAt DESC")
    fun getAllFiles(): Flow<List<FileIndex>>

    // ═══════════════════════════════════════════════════════════════════
    // Sprint 2.5 FIX: Exclude PULL files from the upload queue.
    // Old query: WHERE shouldBackup = 1 AND isBackedUp = 0
    // New query: adds AND syncDirection = 'PUSH'
    //
    // Without this, every file downloaded from the Master would be
    // re-uploaded back to the Master, creating an infinite sync loop.
    // ═══════════════════════════════════════════════════════════════════
    @Query("SELECT * FROM file_index WHERE shouldBackup = 1 AND isBackedUp = 0 AND syncDirection = 'PUSH'")
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

    @Query("DELETE FROM file_index")
    suspend fun clearAllFiles()

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

    // ═══════════════════════════════════════════════════════════════════
    // Sprint 2.5: Pull-sync queries
    // ═══════════════════════════════════════════════════════════════════

    /** Check if we already have a file with this checksum (any sync direction) */
    @Query("SELECT COUNT(*) FROM file_index WHERE checksum = :checksum")
    suspend fun countByChecksum(checksum: String): Int

    /** Get all pulled files (for display/debugging) */
    @Query("SELECT * FROM file_index WHERE syncDirection = 'PULL' ORDER BY backedUpAt DESC")
    fun getPulledFiles(): Flow<List<FileIndex>>

    /** Count pulled files */
    @Query("SELECT COUNT(*) FROM file_index WHERE syncDirection = 'PULL'")
    suspend fun countPulledFiles(): Int
}