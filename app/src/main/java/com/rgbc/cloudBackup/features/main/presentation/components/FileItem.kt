// File: FileItem.kt
package com.rgbc.cloudBackup.features.main.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FileItem(file: FileIndex, modifier: Modifier = Modifier) {
    val safeDisplayName = "File_${file.name.hashCode().toString().takeLast(8)}"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {}
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = safeDisplayName) // ← Show safe name instead of actual filename
            Text(text = "${file.fileType} • ${formatFileSize(file.size)}")
            Text(text = "Modified: ${formatDate(file.modifiedAt)}")
            if (file.isBackedUp) {
                Text(text = "✓ Backed up")
            }
        }
    }
}


// Helpers
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
