package com.rgbc.cloudBackup.features.main.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.features.main.presentation.components.ErrorCard
import com.rgbc.cloudBackup.features.main.presentation.components.FileItem
import com.rgbc.cloudBackup.features.main.presentation.components.ScanProgressCard
import com.rgbc.cloudBackup.features.main.presentation.components.StatisticsCard
import timber.log.Timber
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToAuth: () -> Unit = {},
    onNavigateToDirectories: () -> Unit = {}, // Add this parameter
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(initialValue = MainUiState())
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val filesToBackup by viewModel.filesToBackup.collectAsStateWithLifecycle(initialValue = emptyList())

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000) // 10 seconds
            try {
                viewModel.triggerExternalRefresh("background_sync")
            } catch (e: Exception) {
                Timber.e(e, "Background refresh failed")
            }
        }
    }

// In MainScreen.kt, REPLACE the existing DisposableEffect with this:
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Timber.d("🔄 MainScreen resumed, force refreshing file status")
                    viewModel.refreshData()
                }
                Lifecycle.Event.ON_START -> {
                    Timber.d("🔄 MainScreen started, refreshing data")
                    viewModel.refreshData()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }




    Timber.d("MainScreen composed with ${allFiles.size} files and scanningState = ${uiState.isScanning}")

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Header
        Text(
            text = "CloudBackup App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        StatisticsCard(statistics = uiState.backupStatistics)

        uiState.scanProgress?.let { progress ->
            Timber.d("Scan progress received: $progress")
            ScanProgressCard(progress = progress)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Directory Management Card
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
                    text = "📁 Directory Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Select folders to backup automatically",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onNavigateToDirectories, // Now this parameter exists
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manage Directories")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    Timber.i("Refresh button clicked")
                    viewModel.refreshData()
                },
                enabled = !uiState.isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(16.dp))

        // Display errors if present
        uiState.errorMessage?.let { message ->
            Timber.w("Error displayed: $message")
//            ErrorCard(errorMessage = message, onDismiss = viewModel::clearError)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Files list
        LazyColumn {
            item {
                Text(
                    text = "All Files (${allFiles.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(allFiles) { file ->
                FileItem(file = file, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (allFiles.isEmpty() && !uiState.isScanning) {
                item {
                    Text(
                        text = "No files found. Add a test file to get started!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}