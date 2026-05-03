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
        val BACKUP_INTERVAL_HOURS = longPreferencesKey("backup_interval_hours")
        // Sprint 2.5: Pull sync from Master
        val PULL_SYNC_ENABLED = booleanPreferencesKey("pull_sync_enabled")
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
        Timber.i("🧹 DataStore preferences cleared on logout")
    }

    val downloadPath: Flow<String> = context.dataStore.data.map { it[DOWNLOAD_PATH] ?: getDefaultDownloadPath() }
    suspend fun setDownloadPath(path: String) { context.dataStore.edit { it[DOWNLOAD_PATH] = path } }

    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_BACKUP_ENABLED] ?: true }
    suspend fun setAutoBackupEnabled(enabled: Boolean) { context.dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled } }

    val backupIntervalHours: Flow<Long> = context.dataStore.data.map { it[BACKUP_INTERVAL_HOURS] ?: 1L }
    suspend fun setBackupIntervalHours(hours: Long) { context.dataStore.edit { it[BACKUP_INTERVAL_HOURS] = hours.coerceIn(1, 24) } }

    val backupOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[BACKUP_ON_WIFI_ONLY] ?: true }
    suspend fun setBackupOnWifiOnly(enabled: Boolean) { context.dataStore.edit { it[BACKUP_ON_WIFI_ONLY] = enabled } }

    val backupOnCharging: Flow<Boolean> = context.dataStore.data.map { it[BACKUP_ON_CHARGING] ?: false }
    suspend fun setBackupOnCharging(enabled: Boolean) { context.dataStore.edit { it[BACKUP_ON_CHARGING] = enabled } }

    val maxConcurrentUploads: Flow<Int> = context.dataStore.data.map { it[MAX_CONCURRENT_UPLOADS] ?: 2 }
    suspend fun setMaxConcurrentUploads(count: Int) { context.dataStore.edit { it[MAX_CONCURRENT_UPLOADS] = count.coerceIn(1, 5) } }

    val maxFileSizeMB: Flow<Int> = context.dataStore.data.map { it[MAX_FILE_SIZE_MB] ?: 100 }
    suspend fun setMaxFileSizeMB(sizeMB: Int) { context.dataStore.edit { it[MAX_FILE_SIZE_MB] = sizeMB.coerceIn(1, 1000) } }

    val selectedBackupDirs: Flow<Set<String>> = context.dataStore.data.map { it[SELECTED_BACKUP_DIRS] ?: emptySet() }
    suspend fun setSelectedBackupDirs(dirs: Set<String>) { context.dataStore.edit { it[SELECTED_BACKUP_DIRS] = dirs } }

    val encryptionEnabled: Flow<Boolean> = context.dataStore.data.map { it[ENCRYPTION_ENABLED] ?: true }
    suspend fun setEncryptionEnabled(enabled: Boolean) { context.dataStore.edit { it[ENCRYPTION_ENABLED] = enabled } }

    val compressionEnabled: Flow<Boolean> = context.dataStore.data.map { it[COMPRESSION_ENABLED] ?: true }
    suspend fun setCompressionEnabled(enabled: Boolean) { context.dataStore.edit { it[COMPRESSION_ENABLED] = enabled } }

    val retryFailedUploads: Flow<Boolean> = context.dataStore.data.map { it[RETRY_FAILED_UPLOADS] ?: true }
    suspend fun setRetryFailedUploads(enabled: Boolean) { context.dataStore.edit { it[RETRY_FAILED_UPLOADS] = enabled } }

    // Sprint 2.5: Pull sync toggle
    val pullSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[PULL_SYNC_ENABLED] ?: true }
    suspend fun setPullSyncEnabled(enabled: Boolean) { context.dataStore.edit { it[PULL_SYNC_ENABLED] = enabled } }

    private fun getDefaultDownloadPath(): String = try {
        context.getExternalFilesDir("Downloads")?.absolutePath ?: "${context.filesDir}/Downloads"
    } catch (e: Exception) { "${context.filesDir}/Downloads" }
}

data class BackupSettings(
    val downloadPath: String, val autoBackupEnabled: Boolean, val backupOnWifiOnly: Boolean,
    val backupOnCharging: Boolean, val maxConcurrentUploads: Int, val maxFileSizeMB: Int,
    val encryptionEnabled: Boolean, val compressionEnabled: Boolean, val retryFailedUploads: Boolean,
    val backupIntervalHours: Long = 1L, val pullSyncEnabled: Boolean = true
)