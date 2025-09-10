package com.rgbc.cloudBackup.features.directory.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupDirectoryDao {
    @Query("SELECT * FROM backup_directories WHERE is_enabled = 1 ORDER BY created_at DESC")
    fun getAllDirectories(): Flow<List<BackupDirectoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDirectory(directory: BackupDirectoryEntity)

    @Query("DELETE FROM backup_directories WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT COUNT(*) FROM backup_directories WHERE uri = :uri")
    suspend fun countByUri(uri: String): Int

    @Update
    suspend fun updateDirectory(directory: BackupDirectoryEntity)

    @Query("SELECT * FROM backup_directories WHERE id = :id")
    suspend fun getDirectoryById(id: String): BackupDirectoryEntity?
}