package com.rgbc.cloudBackup.di

import android.content.Context
import androidx.room.Room
import com.rgbc.cloudBackup.core.data.database.CloudBackupDatabase
import com.rgbc.cloudBackup.core.data.database.DatabaseMigrations
import com.rgbc.cloudBackup.core.data.database.entity.BackupQueueDao
import com.rgbc.cloudBackup.core.data.database.entity.FileIndexDao
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CloudBackupDatabase {
        return Room.databaseBuilder(
            context,
            CloudBackupDatabase::class.java,
            "cloud_backup_database"
        )
            // Wire every migration from the unified registry
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            // fallbackToDestructiveMigration() is intentionally REMOVED.
            // All schema changes MUST have a corresponding Migration object.
            .build()
    }

    @Provides
    @Singleton
    fun provideFileIndexDao(database: CloudBackupDatabase): FileIndexDao {
        return database.fileIndexDao()
    }

    @Provides
    @Singleton
    fun provideBackupDirectoryDao(database: CloudBackupDatabase): BackupDirectoryDao {
        return database.backupDirectoryDao()
    }

    @Provides
    @Singleton
    fun provideBackupQueueDao(database: CloudBackupDatabase): BackupQueueDao {
        return database.backupQueueDao()
    }
}