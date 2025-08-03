package com.rgbc.cloudBackup.core.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectoryProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Returns a list of top-level directories to scan for files.
     */
    fun getScannableDirectories(): List<String> {
        val dirs = mutableListOf<String>()

        // Public external storage
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            dirs += Environment.getExternalStorageDirectory().absolutePath
        }

        // App-specific external directories
        context.getExternalFilesDir(null)?.absolutePath?.let { dirs += it }
        context.getExternalFilesDirs(null).forEach { file ->
            file?.absolutePath?.let { dirs += it }
        }

        Timber.d("Scannable directories: $dirs")
        return dirs.distinct().filter { File(it).exists() && File(it).canRead() }
    }

    /**
     * Returns or creates a backup output directory.
     */
    fun getBackupDirectory(): String {
        val backupDir = File(context.getExternalFilesDir("backups"), "files")
        if (!backupDir.exists()) backupDir.mkdirs()
        Timber.d("Backup directory: ${backupDir.absolutePath}")
        return backupDir.absolutePath
    }
}
