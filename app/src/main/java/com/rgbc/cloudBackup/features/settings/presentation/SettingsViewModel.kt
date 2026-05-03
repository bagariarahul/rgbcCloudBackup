package com.rgbc.cloudBackup.features.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.preferences.SettingsManager
import com.rgbc.cloudBackup.core.service.BackupScheduler
import com.rgbc.cloudBackup.core.service.SyncPullScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SettingsUiState(
    val autoBackupEnabled: Boolean = true,
    val wifiOnly: Boolean = true,
    val backupIntervalHours: Long = 1L,
    val encryptionEnabled: Boolean = true,
    val compressionEnabled: Boolean = true,
    // Sprint 2.5: Pull sync from Master
    val pullSyncEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsManager.autoBackupEnabled,
                settingsManager.backupOnWifiOnly,
                settingsManager.backupIntervalHours,
                settingsManager.encryptionEnabled,
                settingsManager.compressionEnabled,
                settingsManager.pullSyncEnabled
            ) { values ->
                SettingsUiState(
                    autoBackupEnabled = values[0] as Boolean,
                    wifiOnly = values[1] as Boolean,
                    backupIntervalHours = values[2] as Long,
                    encryptionEnabled = values[3] as Boolean,
                    compressionEnabled = values[4] as Boolean,
                    pullSyncEnabled = values[5] as Boolean
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // ── Toggle: Enable Background Backups (Push) ────────────────────────
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAutoBackupEnabled(enabled)
            if (enabled) {
                val s = _uiState.value
                BackupScheduler.schedulePeriodic(context, s.backupIntervalHours, s.wifiOnly)
                Timber.i("⏰ Background backups enabled (${s.backupIntervalHours}h)")
            } else {
                BackupScheduler.cancelAll(context)
                Timber.i("⏰ Background backups disabled")
            }
        }
    }

    // ── Toggle: Wi-Fi Only ──────────────────────────────────────────────
    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBackupOnWifiOnly(enabled)
            val s = _uiState.value
            if (s.autoBackupEnabled) {
                BackupScheduler.schedulePeriodic(context, s.backupIntervalHours, enabled)
            }
        }
    }

    // ── Selector: Backup Interval ───────────────────────────────────────
    fun setBackupInterval(hours: Long) {
        viewModelScope.launch {
            settingsManager.setBackupIntervalHours(hours)
            val s = _uiState.value
            if (s.autoBackupEnabled) {
                BackupScheduler.schedulePeriodic(context, hours, s.wifiOnly)
            }
        }
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setEncryptionEnabled(enabled) }
    }

    fun setCompressionEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setCompressionEnabled(enabled) }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Sprint 2.5: Toggle: Pull Sync from Master
    //
    // When enabled:  SyncPullWorker runs every 15 minutes to download
    //                new files from the Master Node.
    // When disabled: SyncPullWorker is cancelled. No files are pulled.
    // ═══════════════════════════════════════════════════════════════════
    fun setPullSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setPullSyncEnabled(enabled)
            if (enabled) {
                val s = _uiState.value
                SyncPullScheduler.schedulePeriodic(
                    context = context,
                    intervalMinutes = 15,
                    wifiOnly = s.wifiOnly
                )
                Timber.i("📥 Pull sync enabled (every 15 min)")
            } else {
                SyncPullScheduler.cancelAll(context)
                Timber.i("📥 Pull sync disabled")
            }
        }
    }
}