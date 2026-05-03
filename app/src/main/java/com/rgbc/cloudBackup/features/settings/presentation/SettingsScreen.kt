package com.rgbc.cloudBackup.features.settings.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgbc.cloudBackup.features.auth.presentation.AuthViewModel
import timber.log.Timber

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var showIntervalPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Timber.d("⚙️ SettingsScreen loaded")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Account Section
            item {
                SettingsSection(
                    title = "Account",
                    items = listOf(
                        SettingsItem.Info(
                            title = getEmailFromAuthState(authState),
                            subtitle = "Signed in"
                        ),
                        SettingsItem.Action(
                            title = "Sign Out",
                            subtitle = "Sign out of your account",
                            action = {
                                Timber.i("🚪 User initiated logout from settings")
                                authViewModel.logout()
                            },
                            dangerous = true
                        )
                    )
                )
            }

            // ── Backup Section (Push) ───────────────────────────────
            item {
                SettingsSection(
                    title = "Backup (Push to Master)",
                    items = listOf(
                        SettingsItem.Toggle(
                            title = "Background Backups",
                            subtitle = if (settings.autoBackupEnabled)
                                "Running every ${formatInterval(settings.backupIntervalHours)}"
                            else
                                "Disabled — files only upload manually",
                            enabled = settings.autoBackupEnabled,
                            onToggle = { settingsViewModel.setAutoBackupEnabled(it) }
                        ),
                        SettingsItem.Action(
                            title = "Backup Interval",
                            subtitle = "Every ${formatInterval(settings.backupIntervalHours)}",
                            action = { showIntervalPicker = true }
                        ),
                        SettingsItem.Toggle(
                            title = "Wi-Fi Only",
                            subtitle = if (settings.wifiOnly)
                                "Sync pauses on mobile data"
                            else
                                "Sync runs on any network",
                            enabled = settings.wifiOnly,
                            onToggle = { settingsViewModel.setWifiOnly(it) }
                        )
                    )
                )
            }

            // ── P2P Sync Section (Pull) — Sprint 2.5 ───────────────
            item {
                SettingsSection(
                    title = "P2P Sync (Pull from Master)",
                    items = listOf(
                        SettingsItem.Toggle(
                            title = "Pull Files from Master",
                            subtitle = if (settings.pullSyncEnabled)
                                "New files from your PC sync here every 15 min"
                            else
                                "Disabled — this device only uploads",
                            enabled = settings.pullSyncEnabled,
                            onToggle = { settingsViewModel.setPullSyncEnabled(it) }
                        )
                    )
                )
            }

            // Storage Section
            item {
                SettingsSection(
                    title = "Storage",
                    items = listOf(
                        SettingsItem.Info(
                            title = "Used Storage",
                            subtitle = "0 MB / 100 GB"
                        ),
                        SettingsItem.Action(
                            title = "Clear Cache",
                            subtitle = "Clear temporary files and thumbnails",
                            action = {
                                Timber.i("🧹 Clear cache requested")
                            }
                        )
                    )
                )
            }

            // Security Section
            item {
                SettingsSection(
                    title = "Security",
                    items = listOf(
                        SettingsItem.Toggle(
                            title = "Encryption",
                            subtitle = "Encrypt files before upload",
                            enabled = settings.encryptionEnabled,
                            onToggle = { settingsViewModel.setEncryptionEnabled(it) }
                        ),
                        SettingsItem.Toggle(
                            title = "Compression",
                            subtitle = "Compress files to save bandwidth",
                            enabled = settings.compressionEnabled,
                            onToggle = { settingsViewModel.setCompressionEnabled(it) }
                        )
                    )
                )
            }

            // About Section
            item {
                SettingsSection(
                    title = "About",
                    items = listOf(
                        SettingsItem.Info(
                            title = "Version",
                            subtitle = "2.0.0 (P2P Build)"
                        ),
                        SettingsItem.Action(
                            title = "Privacy Policy",
                            subtitle = "View our privacy policy",
                            action = { Timber.d("📄 Privacy policy requested") }
                        ),
                        SettingsItem.Action(
                            title = "Terms of Service",
                            subtitle = "View terms and conditions",
                            action = { Timber.d("📋 Terms of service requested") }
                        )
                    )
                )
            }
        }
    }

    // ── Interval Picker Dialog ──────────────────────────────────────────
    if (showIntervalPicker) {
        IntervalPickerDialog(
            currentHours = settings.backupIntervalHours,
            onSelect = { hours ->
                settingsViewModel.setBackupInterval(hours)
                showIntervalPicker = false
            },
            onDismiss = { showIntervalPicker = false }
        )
    }
}

// ── Interval Picker ─────────────────────────────────────────────────────

@Composable
fun IntervalPickerDialog(
    currentHours: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(1L, 3L, 6L, 12L, 24L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup Interval") },
        text = {
            Column {
                options.forEach { hours ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(hours) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = hours == currentHours,
                            onClick = { onSelect(hours) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = formatInterval(hours),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatInterval(hours: Long): String = when (hours) {
    1L -> "1 hour"
    else -> "$hours hours"
}

// ── Existing Composable Helpers (unchanged) ─────────────────────────────

@Composable
fun SettingsSection(
    title: String,
    items: List<SettingsItem>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    when (item) {
                        is SettingsItem.Toggle -> {
                            ToggleSettingsItem(
                                title = item.title,
                                subtitle = item.subtitle,
                                enabled = item.enabled,
                                onToggle = item.onToggle
                            )
                        }
                        is SettingsItem.Action -> {
                            ActionSettingsItem(
                                title = item.title,
                                subtitle = item.subtitle,
                                action = item.action,
                                dangerous = item.dangerous
                            )
                        }
                        is SettingsItem.Info -> {
                            InfoSettingsItem(
                                title = item.title,
                                subtitle = item.subtitle
                            )
                        }
                    }

                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ToggleSettingsItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun ActionSettingsItem(
    title: String,
    subtitle: String,
    action: () -> Unit,
    dangerous: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { action() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = if (dangerous) MaterialTheme.colorScheme.error else Color.Unspecified
            )
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoSettingsItem(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

sealed class SettingsItem {
    data class Toggle(val title: String, val subtitle: String, val enabled: Boolean, val onToggle: (Boolean) -> Unit) : SettingsItem()
    data class Action(val title: String, val subtitle: String, val action: () -> Unit, val dangerous: Boolean = false) : SettingsItem()
    data class Info(val title: String, val subtitle: String) : SettingsItem()
}

fun getEmailFromAuthState(authState: com.rgbc.cloudBackup.core.auth.AuthState): String {
    return when (authState) {
        is com.rgbc.cloudBackup.core.auth.AuthState.Authenticated -> authState.user.email
        else -> "Unknown"
    }
}