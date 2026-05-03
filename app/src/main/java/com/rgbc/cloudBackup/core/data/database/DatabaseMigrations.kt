package com.rgbc.cloudBackup.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

/**
 * Database migrations for schema changes.
 *
 * History:
 *   1→2: Added lastAttemptedAt, errorMessage
 *   2→3: Added fileType, mimeType
 *   3→4: Added shouldBackup/isBackedUp indexes
 *   4→5: Created backup_directories table
 *   5→6: Created backup_queue table + serverFileId column
 *   6→7: Added syncDirection column (Sprint 2.5 — P2P pull sync)
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

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
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

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Timber.i("Running MIGRATION_5_6: Adding backup_queue table and serverFileId column")

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

    // ═══════════════════════════════════════════════════════════════════
    // Sprint 2.5: Add syncDirection column for P2P bidirectional sync
    //
    // This column prevents pulled files from being re-uploaded:
    //   "PUSH" = file originated on this device → upload to Master
    //   "PULL" = file downloaded from Master → do NOT re-upload
    //
    // All existing rows get "PUSH" (they were all local files before P2P).
    // ═══════════════════════════════════════════════════════════════════
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Timber.i("Running MIGRATION_6_7: Adding syncDirection column for P2P pull sync")

            // Safe add: check if column already exists (defensive)
            val cursor = db.query("PRAGMA table_info(file_index)")
            val columnNames = mutableListOf<String>()
            val nameIdx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIdx >= 0) columnNames.add(cursor.getString(nameIdx))
            }
            cursor.close()

            if ("syncDirection" !in columnNames) {
                db.execSQL("ALTER TABLE file_index ADD COLUMN syncDirection TEXT NOT NULL DEFAULT 'PUSH'")
                Timber.i("  Added syncDirection column with default 'PUSH'")
            } else {
                Timber.i("  syncDirection column already exists, skipping")
            }

            // Add index for fast filtering in getFilesToBackup()
            db.execSQL("CREATE INDEX IF NOT EXISTS index_file_index_syncDirection ON file_index(syncDirection)")

            Timber.i("MIGRATION_6_7 complete")
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7
    )
}