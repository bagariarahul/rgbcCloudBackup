package com.rgbc.cloudBackup.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.rgbc.cloudBackup.features.directory.data.BackupDirectoryDao
import com.rgbc.cloudBackup.features.directory.data.BackupDirectory  // FIXED: Use BackupDirectory instead of BackupDirectoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentDirectoryRepository @Inject constructor(
    private val directoryDao: BackupDirectoryDao,
    @ApplicationContext private val context: Context
) {

    suspend fun addDirectory(uri: Uri): Result<BackupDirectory> {  // FIXED: Return type
        return try {
            val uriString = uri.toString()

            // Check for duplicates
            if (directoryDao.countByUri(uriString) > 0) {
                return Result.failure(Exception("Directory already exists"))
            }

            // Take persistent permission - BOTH READ AND WRITE
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val documentFile = DocumentFile.fromTreeUri(context, uri)
            val directory = BackupDirectory(  // FIXED: Use BackupDirectory
                uri = uriString,
                displayName = documentFile?.name ?: "Unknown Directory",
                path = getDisplayPath(uri)
            )

            directoryDao.insertDirectory(directory)
            Timber.d("✅ Directory added and persisted: ${directory.displayName}")
            Result.success(directory)

        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to add directory")
            Result.failure(e)
        }
    }

    suspend fun restorePersistedDirectories(): List<BackupDirectory> {  // FIXED: Return type
        return try {
            Timber.d("🔄 Restoring persisted directories...")
            val persistedUris = context.contentResolver.persistedUriPermissions
            Timber.d("📋 Found ${persistedUris.size} persisted URI permissions")

            // Get all directories from database
            val allDbDirectories = directoryDao.getAllDirectories().first()
            Timber.d("📊 Found ${allDbDirectories.size} directories in database")

            val validDirectories = mutableListOf<BackupDirectory>()  // FIXED: Type

            allDbDirectories.forEach { directory ->
                val uri = Uri.parse(directory.uri)  // FIXED: Now uri exists

                // Check if we still have permission
                val hasPermission = persistedUris.any {
                    it.uri == uri && it.isReadPermission
                }

                if (hasPermission) {
                    // Verify directory still exists and accessible
                    try {
                        val documentFile = DocumentFile.fromTreeUri(context, uri)
                        if (documentFile?.exists() == true && documentFile.canRead()) {
                            validDirectories.add(directory)
                            Timber.d("✅ Restored directory: ${directory.displayName}")
                        } else {
                            Timber.w("📁 Directory exists but not accessible: ${directory.displayName}")
                            // Don't delete - might be temporary permission issue
                            validDirectories.add(directory)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "⚠️ Error checking directory: ${directory.displayName}")
                        // Keep directory in case it's a temporary issue
                        validDirectories.add(directory)
                    }
                } else {
                    // Lost permission - try to retake it
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        validDirectories.add(directory)
                        Timber.d("🔄 Retook permission for: ${directory.displayName}")
                    } catch (e: Exception) {
                        // Only remove if we can't retake permission
                        directoryDao.deleteByUri(directory.uri)  // FIXED: Now deleteByUri exists
                        Timber.w("❌ Removed directory without recoverable permission: ${directory.displayName}")
                    }
                }
            }

            Timber.d("✅ Directory restoration complete: ${validDirectories.size} directories restored")
            validDirectories
        } catch (e: Exception) {
            Timber.e(e, "💥 Failed to restore directories")
            emptyList()
        }
    }

    fun getAllDirectories(): Flow<List<BackupDirectory>> {  // FIXED: Return type matches DAO
        return directoryDao.getAllDirectories()
    }

    suspend fun removeDirectory(uri: String) {
        try {
            directoryDao.deleteByUri(uri)  // FIXED: Method now exists
            // Release persistent permission
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to release URI permission")
            }
            Timber.d("✅ Directory removed: $uri")
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to remove directory")
        }
    }

    private fun getDisplayPath(uri: Uri): String {
        return uri.pathSegments.lastOrNull() ?: uri.toString()
    }
}