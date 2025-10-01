package com.rgbc.cloudBackup.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_ON_WIFI_ONLY = booleanPreferencesKey("backup_on_wifi_only")
        val BACKUP_ON_CHARGING = booleanPreferencesKey("backup_on_charging")
        val MAX_CONCURRENT_UPLOADS = intPreferencesKey("max_concurrent_uploads")
        val MAX_FILE_SIZE_MB = intPreferencesKey("max_file_size_mb")
        val RETRY_FAILED_UPLOADS = booleanPreferencesKey("retry_failed_uploads")
        val SELECTED_BACKUP_DIRS = stringSetPreferencesKey("selected_backup_dirs")
        val COMPRESSION_ENABLED = booleanPreferencesKey("compression_enabled")
        val ENCRYPTION_ENABLED = booleanPreferencesKey("encryption_enabled")
    }

    // Download settings
    val downloadPath: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[DOWNLOAD_PATH] ?: getDefaultDownloadPath()
    }

    suspend fun setDownloadPath(path: String) {
        context.dataStore.edit { preferences ->
            preferences[DOWNLOAD_PATH] = path
        }
        Timber.d("📁 Download path updated: $path")
    }

    // Auto backup settings
    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_BACKUP_ENABLED] ?: true
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_BACKUP_ENABLED] = enabled
        }
        Timber.d("🔄 Auto backup ${if (enabled) "enabled" else "disabled"}")
    }

    // Network settings
    val backupOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BACKUP_ON_WIFI_ONLY] ?: true
    }

    suspend fun setBackupOnWifiOnly(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BACKUP_ON_WIFI_ONLY] = enabled
        }
    }

    val backupOnCharging: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[BACKUP_ON_CHARGING] ?: false
    }

    suspend fun setBackupOnCharging(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BACKUP_ON_CHARGING] = enabled
        }
    }

    // Performance settings
    val maxConcurrentUploads: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_CONCURRENT_UPLOADS] ?: 2
    }

    suspend fun setMaxConcurrentUploads(count: Int) {
        val validCount = count.coerceIn(1, 5) // Limit between 1-5
        context.dataStore.edit { preferences ->
            preferences[MAX_CONCURRENT_UPLOADS] = validCount
        }
    }

    val maxFileSizeMB: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MAX_FILE_SIZE_MB] ?: 100 // 100MB default
    }

    suspend fun setMaxFileSizeMB(sizeMB: Int) {
        val validSize = sizeMB.coerceIn(1, 1000) // 1MB to 1GB
        context.dataStore.edit { preferences ->
            preferences[MAX_FILE_SIZE_MB] = validSize
        }
    }

    // Directory selection
    val selectedBackupDirs: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_BACKUP_DIRS] ?: emptySet()
    }

    suspend fun setSelectedBackupDirs(dirs: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_BACKUP_DIRS] = dirs
        }
        Timber.d("📂 Selected backup directories: ${dirs.size}")
    }

    // Security settings
    val encryptionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ENCRYPTION_ENABLED] ?: true
    }

    suspend fun setEncryptionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENCRYPTION_ENABLED] = enabled
        }
    }

    val compressionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[COMPRESSION_ENABLED] ?: true
    }

    suspend fun setCompressionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[COMPRESSION_ENABLED] = enabled
        }
    }

    // Retry settings
    val retryFailedUploads: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[RETRY_FAILED_UPLOADS] ?: true
    }

    suspend fun setRetryFailedUploads(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[RETRY_FAILED_UPLOADS] = enabled
        }
    }

    // Helper functions
    private fun getDefaultDownloadPath(): String {
        return try {
            val downloadsDir = context.getExternalFilesDir("Downloads")
            downloadsDir?.absolutePath ?: "${context.filesDir}/Downloads"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get default download path")
            "${context.filesDir}/Downloads"
        }
    }

    // Get all settings as data class
    suspend fun getAllSettings(): BackupSettings {
        return BackupSettings(
            downloadPath = downloadPath.map { it }.toString(),
            autoBackupEnabled = autoBackupEnabled.map { it }.toString().toBoolean(),
            backupOnWifiOnly = backupOnWifiOnly.map { it }.toString().toBoolean(),
            backupOnCharging = backupOnCharging.map { it }.toString().toBoolean(),
            maxConcurrentUploads = maxConcurrentUploads.map { it }.toString().toInt(),
            maxFileSizeMB = maxFileSizeMB.map { it }.toString().toInt(),
            encryptionEnabled = encryptionEnabled.map { it }.toString().toBoolean(),
            compressionEnabled = compressionEnabled.map { it }.toString().toBoolean(),
            retryFailedUploads = retryFailedUploads.map { it }.toString().toBoolean()
        )
    }
}

data class BackupSettings(
    val downloadPath: String,
    val autoBackupEnabled: Boolean,
    val backupOnWifiOnly: Boolean,
    val backupOnCharging: Boolean,
    val maxConcurrentUploads: Int,
    val maxFileSizeMB: Int,
    val encryptionEnabled: Boolean,
    val compressionEnabled: Boolean,
    val retryFailedUploads: Boolean
)