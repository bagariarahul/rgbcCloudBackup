package com.rgbc.cloudBackup.di

import android.content.Context
import androidx.room.Room
import com.rgbc.cloudBackup.core.data.database.CloudBackupDatabase
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
            .fallbackToDestructiveMigration() // TEMPORARY: Clears database on schema change
            .build()
    }

    @Provides
    @Singleton
    fun provideFileIndexDao(database: CloudBackupDatabase): FileIndexDao {
        return database.fileIndexDao()
    }

    // ADD: BackupDirectoryDao provider
    @Provides
    @Singleton
    fun provideBackupDirectoryDao(database: CloudBackupDatabase): BackupDirectoryDao {
        return database.backupDirectoryDao()
    }
}