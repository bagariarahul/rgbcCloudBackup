package com.rgbc.cloudBackup.core.domain.provider

import android.content.Context
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

    fun getScannableDirectories(): List<String> {
        val directories = mutableListOf<String>()

        try {
            // External storage directories
            val externalDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            )

            // App-specific directories
            val appDirs = listOfNotNull(
                context.getExternalFilesDir(null),
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            )

            // Filter directories that exist and are readable
            (externalDirs + appDirs).forEach { dir ->
                if (dir.exists() && dir.canRead()) {
                    directories.add(dir.absolutePath)
                    Timber.d("Added scannable directory: ${dir.absolutePath}")
                } else {
                    Timber.w("Directory not accessible: ${dir.absolutePath}")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error getting scannable directories")
        }

        return directories
    }

    fun isDirectoryAccessible(path: String): Boolean {
        return try {
            val dir = File(path)
            dir.exists() && dir.isDirectory && dir.canRead()
        } catch (e: Exception) {
            Timber.e(e, "Error checking directory accessibility: $path")
            false
        }
    }

    fun getDirectorySize(path: String): Long {
        return try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating directory size: $path")
            0L
        }
    }

    fun getFileCount(path: String): Int {
        return try {
            val dir = File(path)
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().filter { it.isFile }.count()
            } else {
                0
            }
        } catch (e: Exception) {
            Timber.e(e, "Error counting files in directory: $path")
            0
        }
    }
}
