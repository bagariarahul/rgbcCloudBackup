package com.rgbc.cloudBackup.core.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rgbc.cloudBackup.core.data.database.entity.*
import com.rgbc.cloudBackup.features.directory.data.BackupDirectory
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryDao
import java.util.Date

@Database(
    entities = [
        FileIndex::class,
        BackupDirectory::class  // ADD: Directory entity
    ],
    version = 5, // Keep your current version
    exportSchema = false // Set to false for now to avoid schema conflicts
)
@TypeConverters(DateConverter::class)
abstract class CloudBackupDatabase : RoomDatabase() {

    // Existing DAOs
    abstract fun fileIndexDao(): FileIndexDao

    // ADD: Directory DAO
    abstract fun backupDirectoryDao(): BackupDirectoryDao

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new columns to existing table if needed
            database.execSQL("ALTER TABLE backup_directories ADD COLUMN uri TEXT DEFAULT ''")
            database.execSQL("ALTER TABLE backup_directories ADD COLUMN display_name TEXT DEFAULT ''")

            // Create index on URI
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backup_directories_uri ON backup_directories(uri)")
        }
    }
}