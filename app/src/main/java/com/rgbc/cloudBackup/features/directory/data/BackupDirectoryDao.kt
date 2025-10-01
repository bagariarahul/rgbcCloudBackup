package com.rgbc.cloudBackup.features.directory.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface BackupDirectoryDao {

    @Query("SELECT * FROM backup_directories ORDER BY display_name ASC")
    fun getAllDirectories(): Flow<List<BackupDirectory>>

    @Query("SELECT * FROM backup_directories WHERE is_enabled = 1 ORDER BY display_name ASC")
    fun getEnabledDirectories(): Flow<List<BackupDirectory>>

    @Query("SELECT * FROM backup_directories WHERE path = :path LIMIT 1")
    suspend fun getDirectoryByPath(path: String): BackupDirectory?

    @Query("SELECT * FROM backup_directories WHERE uri = :uri LIMIT 1")
    suspend fun getDirectoryByUri(uri: String): BackupDirectory?

    // ADD: Missing count method
    @Query("SELECT COUNT(*) FROM backup_directories WHERE uri = :uri")
    suspend fun countByUri(uri: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectory(directory: BackupDirectory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectories(directories: List<BackupDirectory>): List<Long>

    @Update
    suspend fun updateDirectory(directory: BackupDirectory)

    @Delete
    suspend fun deleteDirectory(directory: BackupDirectory)

    @Query("UPDATE backup_directories SET is_enabled = :enabled WHERE id = :id")
    suspend fun updateDirectoryEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE backup_directories SET file_count = :count, total_size = :size, last_scanned = :scanned WHERE id = :id")
    suspend fun updateDirectoryStats(id: Long, count: Int, size: Long, scanned: Date)

    @Query("DELETE FROM backup_directories WHERE path = :path")
    suspend fun deleteDirectoryByPath(path: String)

    // ADD: Missing delete by URI method
    @Query("DELETE FROM backup_directories WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT COUNT(*) FROM backup_directories WHERE is_enabled = 1")
    suspend fun getEnabledDirectoryCount(): Int
}