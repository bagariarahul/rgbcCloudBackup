// File: MainScreen.kt
package com.rgbc.cloudBackup.features.main.presentation

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.domain.model.BackupStats
import com.rgbc.cloudBackup.features.main.presentation.components.ErrorCard
import com.rgbc.cloudBackup.features.main.presentation.components.FileItem
import com.rgbc.cloudBackup.features.main.presentation.components.ScanProgressCard
import com.rgbc.cloudBackup.features.main.presentation.components.StatisticsCard
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle(initialValue = MainUiState())
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val filesToBackup by viewModel.filesToBackup.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(
        modifier = modifier
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "CloudBackup App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Summary statistics
        StatisticsCard(statistics = uiState.backupStatistics)

        // Scan progress
        uiState.scanProgress?.let { progress ->
            ScanProgressCard(progress = progress)
            Spacer(modifier = Modifier.height(16.dp))
        }

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
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh")
            }

            Button(
                onClick = { viewModel.addTestFile() },
                enabled = !uiState.isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Test File")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Test File")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        uiState.errorMessage?.let { message ->
            ErrorCard(errorMessage = message, onDismiss = viewModel::clearError)
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
