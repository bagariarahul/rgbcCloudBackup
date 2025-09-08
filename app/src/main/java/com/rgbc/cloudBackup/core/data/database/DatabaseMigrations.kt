package com.rgbc.cloudBackup.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for schema changes
 */
object DatabaseMigrations {

    /**
     * Migration from version 1 to 2
     * Adds error tracking fields to FileIndex table
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE file_index 
                ADD COLUMN lastAttemptedAt INTEGER DEFAULT NULL
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE file_index 
                ADD COLUMN errorMessage TEXT DEFAULT NULL
                """.trimIndent()
            )
        }
    }

    /**
     * Migration from version 2 to 3
     * Adds file type and mime type fields
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                ALTER TABLE file_index 
                ADD COLUMN fileType TEXT NOT NULL DEFAULT ''
                """.trimIndent()
            )

            db.execSQL(
                """
                ALTER TABLE file_index 
                ADD COLUMN mimeType TEXT DEFAULT NULL
                """.trimIndent()
            )
        }
    }

    /**
     * Migration from version 3 to 4
     * Adds new indices for better query performance
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add indices for better query performance
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_file_index_shouldBackup 
                ON file_index(shouldBackup)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_file_index_isBackedUp 
                ON file_index(isBackedUp)
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_file_index_lastAttemptedAt 
                ON file_index(lastAttemptedAt)
                """.trimIndent()
            )
        }
    }

    /**
     * All migrations in order
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4
    )
}
