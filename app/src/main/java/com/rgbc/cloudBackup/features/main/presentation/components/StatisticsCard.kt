// File: StatisticsCard.kt
package com.rgbc.cloudBackup.features.main.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbc.cloudBackup.core.domain.model.BackupStats

@Composable
fun StatisticsCard(statistics: BackupStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Total Files: ${statistics.totalFiles}")
            Text("Backed Up: ${statistics.backedUpFiles}")
            Text("Pending: ${statistics.pendingFiles}")
            Text("Failed: ${statistics.failedFiles}")
            Text("Errors: ${statistics.errorCount}")
        }
    }
}
