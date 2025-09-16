package com.rgbc.cloudBackup.features.sync.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgbc.cloudBackup.core.network.api.ServerFileInfo
import com.rgbc.cloudBackup.core.domain.repository.SyncComparisonResult
//import com.rgbc.cloudBackup.core.network.api.ServerFileInfo
import timber.log.Timber
import com.rgbc.cloudBackup.ui.theme.Orange


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSyncScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerSyncViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serverFiles by viewModel.serverFiles.collectAsStateWithLifecycle()
    val syncComparison by viewModel.syncComparison.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        Timber.d("🔄 ServerSyncScreen loaded")
        viewModel.refreshServerData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Server Sync",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = {
                Timber.d("🔄 Manual server sync triggered")
                viewModel.refreshServerData()
            }) {
                Icon(Icons.Default.Sync, contentDescription = "Refresh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Loading state
        if (uiState.isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Syncing with server...")
                }
            }
            return
        }

        // Error state
        uiState.error?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(error, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sync Comparison Summary
        syncComparison?.let { comparison ->
            SyncComparisonCard(comparison = comparison)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Server Files List
        if (serverFiles.isNotEmpty()) {
            Text(
                text = "Server Files (${serverFiles.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(serverFiles) { file ->
                    ServerFileItem(
                        file = file,
                        onCheckIntegrity = { fileName ->
                            Timber.d("🔍 Integrity check requested for: $fileName")
                            viewModel.checkFileIntegrity(fileName)
                        }
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No files on server",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SyncComparisonCard(comparison: SyncComparisonResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Sync Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SyncStat("Matched", comparison.matching.size.toString(), Color.Green)
                SyncStat("Local Only", comparison.localOnly.size.toString(), Color.Blue)
                SyncStat("Server Only", comparison.serverOnly.size.toString(), Orange)
                SyncStat("Corrupted", comparison.corrupted.size.toString(), Color.Red)
            }
        }
    }
}

@Composable
fun SyncStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun ServerFileItem(
    file: ServerFileInfo,
    onCheckIntegrity: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.originalName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Size: ${formatBytes(file.fileSize)} → ${formatBytes(file.encryptedSize)} (encrypted)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Uploaded: ${file.uploadedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Integrity status
                    Icon(
                        imageVector = if (file.isCorrupted) Icons.Default.Error else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (file.isCorrupted) Color.Red else Color.Green,
                        modifier = Modifier.size(20.dp)
                    )

                    // Check integrity button
                    IconButton(
                        onClick = { onCheckIntegrity(file.fileName) }
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Check Integrity")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "${bytes / 1_073_741_824} GB"
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        bytes >= 1_024 -> "${bytes / 1_024} KB"
        else -> "$bytes B"
    }
}
