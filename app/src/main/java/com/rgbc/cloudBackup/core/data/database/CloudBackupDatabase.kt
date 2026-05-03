package com.rgbc.cloudBackup.core.data.database

import androidx.room.*
import com.rgbc.cloudBackup.core.data.database.entity.*
import com.rgbc.cloudBackup.features.directory.data.BackupDirectory
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryDao

@Database(
    entities = [
        FileIndex::class,
        BackupDirectory::class,
        BackupQueue::class
    ],
    version = 7,                     // Bumped from 6 → 7 for syncDirection column
    exportSchema = true
)
@TypeConverters(DateConverter::class)
abstract class CloudBackupDatabase : RoomDatabase() {

    abstract fun fileIndexDao(): FileIndexDao

    abstract fun backupDirectoryDao(): BackupDirectoryDao

    abstract fun backupQueueDao(): BackupQueueDao
}