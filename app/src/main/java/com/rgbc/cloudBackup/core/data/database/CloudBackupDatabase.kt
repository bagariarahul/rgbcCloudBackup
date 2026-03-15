package com.rgbc.cloudBackup.core.data.database

import androidx.room.*
import com.rgbc.cloudBackup.core.data.database.entity.*
import com.rgbc.cloudBackup.features.directory.data.BackupDirectory
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryDao

@Database(
    entities = [
        FileIndex::class,
        BackupDirectory::class,
        BackupQueue::class           // Was missing — required for BackupQueueDao
    ],
    version = 6,                     // Bumped from 5 → 6 for BackupQueue + serverFileId
    exportSchema = true              // Re-enabled: generates JSON schemas for migration testing
)
@TypeConverters(DateConverter::class)
abstract class CloudBackupDatabase : RoomDatabase() {

    abstract fun fileIndexDao(): FileIndexDao

    abstract fun backupDirectoryDao(): BackupDirectoryDao

    abstract fun backupQueueDao(): BackupQueueDao   // Was missing entirely
}