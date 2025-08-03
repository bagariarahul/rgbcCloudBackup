package com.rgbc.cloudBackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.model.BackupStats
import com.rgbc.cloudBackup.features.main.presentation.MainViewModel
import com.rgbc.cloudBackup.ui.theme.CloudBackupTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgbc.cloudBackup.features.main.presentation.MainUiState
import com.rgbc.cloudBackup.features.main.presentation.components.StatisticsCard


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CloudBackupTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState(initial = MainUiState())
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val filesToBackup by viewModel.filesToBackup.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "CloudBackup App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        StatisticsCard(statistics = uiState.backupStatistics)

        // Stats Card
        StatsCard(
            stats = uiState.backupStatistics,
            isLoading = uiState.isScanning,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.refreshData() },
                enabled = !uiState.isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }

            Button(
                onClick = { viewModel.addTestFile() },
                enabled = !uiState.isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Test File")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error Message
        uiState.errorMessage?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Files List
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
                FileItem(
                    file = file,
                    modifier = Modifier.fillMaxWidth()
                )
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

@Composable
fun StatsCard(
    stats: BackupStats,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Backup Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Files:")
                    Text(stats.totalFiles.toString())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Backed Up:")
                    Text(stats.backedUpFiles.toString())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Pending:")
                    Text(stats.pendingFiles.toString())
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { stats.backupProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun FileItem(
    file: FileIndex,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${file.fileType} • ${formatFileSize(file.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Modified: ${formatDate(file.modifiedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (file.isBackedUp) {
                Text(
                    text = "✓ Backed up",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// Helper functions - moved to top level
fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> "%.1f GB".format(gb)
        mb >= 1 -> "%.1f MB".format(mb)
        kb >= 1 -> "%.1f KB".format(kb)
        else -> "$bytes B"
    }
}

fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    CloudBackupTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Preview - Use actual app to see data")
        }
    }
}
