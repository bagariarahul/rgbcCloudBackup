// File: ScanProgressCard.kt
package com.rgbc.cloudBackup.features.main.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbc.cloudBackup.core.domain.usecase.ScanProgress

@Composable
fun ScanProgressCard(progress: ScanProgress) {
    val (current, total, message) = when (progress) {
        is ScanProgress.Started ->
            Triple(0, progress.totalDirectories, "Starting scan…")
        is ScanProgress.ScanningDirectory ->
            Triple(progress.completed, progress.total, "Scanning: ${progress.path}")
        is ScanProgress.DirectoryCompleted ->
            Triple(progress.completed, progress.total, "Scanned ${progress.filesFound}")
        is ScanProgress.DirectoryFailed ->
            Triple(0, 1, "Failed: ${progress.error}")
        is ScanProgress.SavingToDatabase ->
            Triple(0, progress.fileCount, "Saving to database…")
        is ScanProgress.DatabaseSaved ->
            Triple(progress.savedCount, progress.savedCount, "Saved ${progress.savedCount}")
        is ScanProgress.Completed ->
            Triple(progress.totalFiles, progress.totalFiles, "Completed ${progress.totalFiles}")
        is ScanProgress.Failed ->
            Triple(0, 1, "Error: ${progress.error}")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = message)
            LinearProgressIndicator(
                progress = if (total > 0) current / total.toFloat() else 0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
            Text(text = "$current/$total", modifier = Modifier.padding(top = 8.dp))
        }
    }
}
