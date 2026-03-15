package com.rgbc.cloudBackup.features.settings.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rgbc.cloudBackup.core.preferences.SettingsManager
import com.rgbc.cloudBackup.core.service.BackupScheduler
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
    val compressionEnabled: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Combine all DataStore flows into a single UI state
        viewModelScope.launch {
            combine(
                settingsManager.autoBackupEnabled,
                settingsManager.backupOnWifiOnly,
                settingsManager.backupIntervalHours,
                settingsManager.encryptionEnabled,
                settingsManager.compressionEnabled
            ) { autoBackup, wifiOnly, interval, encryption, compression ->
                SettingsUiState(
                    autoBackupEnabled = autoBackup,
                    wifiOnly = wifiOnly,
                    backupIntervalHours = interval,
                    encryptionEnabled = encryption,
                    compressionEnabled = compression
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    // ── Toggle: Enable Background Backups ────────────────────────────────
    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setAutoBackupEnabled(enabled)

            if (enabled) {
                // Re-schedule with current interval and wifi settings
                val currentState = _uiState.value
                BackupScheduler.schedulePeriodic(
                    context = context,
                    intervalHours = currentState.backupIntervalHours,
                    wifiOnly = currentState.wifiOnly
                )
                Timber.i("⏰ Background backups enabled (${currentState.backupIntervalHours}h)")
            } else {
                BackupScheduler.cancelAll(context)
                Timber.i("⏰ Background backups disabled")
            }
        }
    }

    // ── Toggle: Wi-Fi Only ───────────────────────────────────────────────
    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBackupOnWifiOnly(enabled)

            // Re-schedule with updated constraint if backups are active
            val currentState = _uiState.value
            if (currentState.autoBackupEnabled) {
                BackupScheduler.schedulePeriodic(
                    context = context,
                    intervalHours = currentState.backupIntervalHours,
                    wifiOnly = enabled
                )
                Timber.i("📶 WiFi-only constraint updated: $enabled")
            }
        }
    }

    // ── Selector: Backup Interval ────────────────────────────────────────
    fun setBackupInterval(hours: Long) {
        viewModelScope.launch {
            settingsManager.setBackupIntervalHours(hours)

            // Re-schedule with new interval if backups are active
            val currentState = _uiState.value
            if (currentState.autoBackupEnabled) {
                BackupScheduler.schedulePeriodic(
                    context = context,
                    intervalHours = hours,
                    wifiOnly = currentState.wifiOnly
                )
                Timber.i("⏰ Backup interval changed to ${hours}h")
            }
        }
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setEncryptionEnabled(enabled)
        }
    }

    fun setCompressionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setCompressionEnabled(enabled)
        }
    }
}