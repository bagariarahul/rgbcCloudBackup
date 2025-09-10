package com.rgbc.cloudBackup.core.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.data.database.entity.FileIndexDao
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryEntity
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryDao

@Database(
    entities = [
        FileIndex::class,
        BackupDirectoryEntity::class
    ],
    version = 2, // Increment version for new entity
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CloudBackupDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
    abstract fun backupDirectoryDao(): BackupDirectoryDao // Add this

    companion object {
        @Volatile
        private var INSTANCE: CloudBackupDatabase? = null

        fun getDatabase(context: Context): CloudBackupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CloudBackupDatabase::class.java,
                    "cloud_backup_database"
                )
                    .fallbackToDestructiveMigration() // For development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}