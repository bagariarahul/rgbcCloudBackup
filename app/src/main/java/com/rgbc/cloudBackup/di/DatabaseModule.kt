package com.rgbc.cloudBackup.di

import android.content.Context
import androidx.room.Room
import com.rgbc.cloudBackup.core.data.database.CloudBackupDatabase
import com.rgbc.cloudBackup.core.data.database.entity.FileIndexDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory

import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CloudBackupDatabase {
        // Generate database password (in production, store this securely)
        val passphrase = getDatabasePassword(context)

        // Create SQLCipher support factory
        val supportFactory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            CloudBackupDatabase::class.java,
            "cloud_backup_database"
        )
            .openHelperFactory(supportFactory)  // Use SQLCipher factory
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideFileIndexDao(database: CloudBackupDatabase): FileIndexDao {
        return database.fileIndexDao()
    }

    /**
     * Generate secure database password
     * In production, you should use Android Keystore
     */
    private fun getDatabasePassword(context: Context): ByteArray {
        val basePassword = "CloudBackup_${context.packageName}"
        return basePassword.toByteArray()
    }
}
