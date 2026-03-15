package com.rgbc.cloudBackup.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * Database migrations for schema changes.
 *
 * History: Migrations 1→4 were written but never executed in production
 * because fallbackToDestructiveMigration() was active. They are preserved
 * for completeness but the only migration path that matters for existing
 * users is 5→6.
 */
object DatabaseMigrations {

    // ── Legacy migrations (preserved, never ran in production) ───────────

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE file_index ADD COLUMN lastAttemptedAt INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE file_index ADD COLUMN errorMessage TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE file_index ADD COLUMN fileType TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE file_index ADD COLUMN mimeType TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_file_index_shouldBackup ON file_index(shouldBackup)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_file_index_isBackedUp ON file_index(isBackedUp)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_file_index_lastAttemptedAt ON file_index(lastAttemptedAt)")
        }
    }

    // ── Migration 4→5: Moved here from CloudBackupDatabase.kt ──────────
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create the backup_directories table fresh (v4 had a different
            // table named "BackupDirectory" with an incompatible schema).
            db.execSQL("DROP TABLE IF EXISTS BackupDirectory")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS backup_directories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uri TEXT NOT NULL DEFAULT '',
                    display_name TEXT NOT NULL DEFAULT '',
                    path TEXT NOT NULL,
                    is_enabled INTEGER NOT NULL DEFAULT 1,
                    file_count INTEGER NOT NULL DEFAULT 0,
                    total_size INTEGER NOT NULL DEFAULT 0,
                    last_scanned INTEGER DEFAULT NULL,
                    created_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backup_directories_path ON backup_directories(path)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backup_directories_uri ON backup_directories(uri)")
        }
    }

    // ── Migration 5→6: Adds BackupQueue table + serverFileId column ─────
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Timber.i("Running MIGRATION_5_6: Adding backup_queue table and serverFileId column")

            // 1. Safely add serverFileId to file_index (may already exist
            //    on devices where the entity was updated before this migration).
            val cursor = db.query("PRAGMA table_info(file_index)")
            val columnNames = mutableListOf<String>()
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) columnNames.add(cursor.getString(nameIdx))
            }
            cursor.close()

            if ("serverFileId" !in columnNames) {
                db.execSQL("ALTER TABLE file_index ADD COLUMN serverFileId INTEGER DEFAULT NULL")
            }

            // 2. Create the backup_queue table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS backup_queue (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    file_path TEXT NOT NULL,
                    original_name TEXT NOT NULL,
                    file_size INTEGER NOT NULL,
                    file_hash TEXT,
                    mime_type TEXT,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    priority INTEGER NOT NULL DEFAULT 5,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    max_retries INTEGER NOT NULL DEFAULT 3,
                    server_file_id TEXT,
                    error_message TEXT,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    completed_at INTEGER,
                    next_retry_at INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_backup_queue_status ON backup_queue(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_backup_queue_priority_created_at ON backup_queue(priority, created_at)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backup_queue_file_path ON backup_queue(file_path)")

            // 3. Ensure backup_directories table exists (safety net for
            //    devices that somehow skipped 4→5).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS backup_directories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    uri TEXT NOT NULL DEFAULT '',
                    display_name TEXT NOT NULL DEFAULT '',
                    path TEXT NOT NULL,
                    is_enabled INTEGER NOT NULL DEFAULT 1,
                    file_count INTEGER NOT NULL DEFAULT 0,
                    total_size INTEGER NOT NULL DEFAULT 0,
                    last_scanned INTEGER DEFAULT NULL,
                    created_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backup_directories_path ON backup_directories(path)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_backup_directories_uri ON backup_directories(uri)")

            Timber.i("MIGRATION_5_6 complete")
        }
    }

    /**
     * All migrations in order. Wire this into Room.databaseBuilder via
     * .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
     */
    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6
    )
}