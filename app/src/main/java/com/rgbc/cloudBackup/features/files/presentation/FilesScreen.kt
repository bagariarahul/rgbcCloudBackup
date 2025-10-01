package com.rgbc.cloudBackup.features.files.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.features.main.presentation.MainViewModel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Sync
//import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = hiltViewModel()
) {

//    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 🆕 ADD: View mode state (persistent across navigation)
    var isGridView by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Timber.d("📄 FilesScreen loaded with ${allFiles.size} files")
        viewModel.refreshData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with view toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "My Files",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // 🆕 ADD: View mode toggle and refresh
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // View toggle button
                IconButton(onClick = {
                    isGridView = !isGridView
                    Timber.d("🔄 View mode changed to: ${if (isGridView) "Grid" else "List"}")
                }) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Refresh button
                IconButton(onClick = {
                    Timber.d("🔄 Files screen refresh clicked")
                    viewModel.refreshData()
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Statistics (same as before)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    label = "Total Files",
                    value = allFiles.size.toString()
                )
                StatisticItem(
                    label = "Backed Up",
                    value = allFiles.count { it.isBackedUp }.toString()
                )
                StatisticItem(
                    label = "Pending",
                    value = allFiles.count { !it.isBackedUp }.toString()
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🆕 ENHANCED: Files Display with Grid/List Toggle
        if (allFiles.isEmpty()) {
            // Empty state (same as before)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No files found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Add directories to scan for files",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 🆕 ADD: Grid or List view based on toggle
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allFiles) { file ->
                        FileGridItem(
                            file = file,
                            onAction = { action ->
                                Timber.d("📂 Grid file action: $action for ${file.name}")
                                handleFileAction(file, action, viewModel)
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allFiles) { file ->
                        FileListItem(
                            file = file,
                            onAction = { action ->
                                Timber.d("📂 List file action: $action for ${file.name}")
                                handleFileAction(file, action, viewModel)
                            }
                        )
                    }
                }
            }
        }
    }
}

// 🆕 ADD: Grid item composable
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    file: FileIndex,
    onAction: (FileAction) -> Unit
) {
    var showActionsMenu by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val safeDisplayName = "File_${file.name.hashCode().toString().takeLast(8)}"

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = {
                    Timber.d("📁 Grid file tapped: $safeDisplayName")
                    onAction(FileAction.Info)
                },
                onLongClick = {
                    Timber.d("👆 Grid long press: $safeDisplayName")
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showLongPressMenu = true
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row with menu and status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Status indicator
                if (file.isBackedUp) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Backed up",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Green
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudQueue,
                        contentDescription = "Pending",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Menu button
                Box {
                    IconButton(
                        onClick = { showActionsMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Actions",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showActionsMenu,
                        onDismissRequest = { showActionsMenu = false }
                    ) {
                        FileContextMenuItems(
                            file = file,
                            onAction = { action ->
                                onAction(action)
                                showActionsMenu = false
                            }
                        )
                    }
                }
            }

            // Center - File icon
            Icon(
                imageVector = getFileIcon(file.fileType),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Bottom - File info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = safeDisplayName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = formatBytes(file.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (file.isBackedUp) "✓ Backed up" else "⏳ Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (file.isBackedUp) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        // Long press menu (same as list view)
        DropdownMenu(
            expanded = showLongPressMenu,
            onDismissRequest = { showLongPressMenu = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = safeDisplayName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = { },
                enabled = false
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Quick Upload") },
                onClick = {
                    onAction(FileAction.Upload)
                    showLongPressMenu = false
                },
                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                enabled = !file.isBackedUp
            )

            DropdownMenuItem(
                text = { Text("Quick Download") },
                onClick = {
                    onAction(FileAction.Download)
                    showLongPressMenu = false
                },
                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                enabled = file.isBackedUp
            )

            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    onAction(FileAction.Delete)
                    showLongPressMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileIndex,
    onAction: (FileAction) -> Unit
) {
    var showActionsMenu by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val safeDisplayName = "File_${file.name.hashCode().toString().takeLast(8)}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    Timber.d("📁 File tapped: $safeDisplayName")
                    onAction(FileAction.Info)
                },
                onLongClick = {
                    Timber.d("👆 Long press on file: $safeDisplayName")
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showLongPressMenu = true
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Icon with status overlay
            Box {
                Icon(
                    imageVector = getFileIcon(file.fileType),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                // Status badge
                if (file.isBackedUp) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Backed up",
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd),
                        tint = Color.Green
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // File Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = safeDisplayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatBytes(file.size)} • ${formatDate(file.modifiedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (file.isBackedUp) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (file.isBackedUp) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (file.isBackedUp) "Backed up" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (file.isBackedUp) Color.Green else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Regular tap menu (three dots)
            Box {
                IconButton(onClick = {
                    Timber.d("⚙️ Opening tap menu for: $safeDisplayName")
                    showActionsMenu = true
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                }

                // Regular tap dropdown menu
                DropdownMenu(
                    expanded = showActionsMenu,
                    onDismissRequest = { showActionsMenu = false }
                ) {
                    FileContextMenuItems(
                        file = file,
                        onAction = { action ->
                            onAction(action)
                            showActionsMenu = false
                        }
                    )
                }
            }
        }

        // 🆕 Long press context menu
        DropdownMenu(
            expanded = showLongPressMenu,
            onDismissRequest = { showLongPressMenu = false }
        ) {
            // Header
            DropdownMenuItem(
                text = {
                    Text(
                        text = safeDisplayName,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = { },
                enabled = false
            )

            HorizontalDivider()

            // Quick actions for long press
            DropdownMenuItem(
                text = { Text("Quick Upload") },
                onClick = {
                    Timber.d("🚀 Quick upload from long press")
                    onAction(FileAction.Upload)
                    showLongPressMenu = false
                },
                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                enabled = !file.isBackedUp
            )

            DropdownMenuItem(
                text = { Text("Quick Download") },
                onClick = {
                    Timber.d("📥 Quick download from long press")
                    onAction(FileAction.Download)
                    showLongPressMenu = false
                },
                leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                enabled = file.isBackedUp
            )

            DropdownMenuItem(
                text = { Text("Share Info") },
                onClick = {
                    Timber.d("📋 Share info from long press")
                    onAction(FileAction.Info)
                    showLongPressMenu = false
                },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    Timber.d("🗑️ Delete from long press")
                    onAction(FileAction.Delete)
                    showLongPressMenu = false
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
fun FileContextMenuItems(
    file: FileIndex,
    onAction: (FileAction) -> Unit
) {
    if (!file.isBackedUp) {
        DropdownMenuItem(
            text = { Text("Upload") },
            onClick = { onAction(FileAction.Upload) },
            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) }
        )
    }

    if (file.isBackedUp) {
        DropdownMenuItem(
            text = { Text("Download") },
            onClick = { onAction(FileAction.Download) },
            leadingIcon = { Icon(Icons.Default.CloudDownload, contentDescription = null) }
        )
    }

    DropdownMenuItem(
        text = { Text("Details") },
        onClick = { onAction(FileAction.Info) },
        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
    )

    DropdownMenuItem(
        text = { Text("Delete") },
        onClick = { onAction(FileAction.Delete) },
        leadingIcon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        }
    )
}


@Composable
fun StatisticItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// 🆕 ADD: Individual file progress indicator
@Composable
fun FileActionProgress(
    isVisible: Boolean,
    action: String,
    progress: Float = 0f,
    fileName: String = ""
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Action icon with progress
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Icon(
                        imageVector = when (action) {
                            "Upload" -> Icons.Default.CloudUpload
                            "Download" -> Icons.Default.CloudDownload
                            "Delete" -> Icons.Default.Delete
                            else -> Icons.Default.Sync
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Action details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$action in progress...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (fileName.isNotEmpty()) {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Progress percentage
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}


// File Actions
sealed class FileAction {
    object Upload : FileAction()
    object Download : FileAction()
    object Delete : FileAction()
    object Info : FileAction()
}

fun getFileIcon(fileType: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (fileType.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "jpg", "jpeg", "png", "gif", "heic" -> Icons.Default.Image
        "mp4", "mov", "avi" -> Icons.Default.VideoFile
        "mp3", "wav", "m4a" -> Icons.Default.AudioFile
        "zip", "rar", "7z" -> Icons.Default.Archive
        "doc", "docx", "txt" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_073_741_824 -> "${bytes / 1_073_741_824} GB"
        bytes >= 1_048_576 -> "${bytes / 1_048_576} MB"
        bytes >= 1_024 -> "${bytes / 1_024} KB"
        else -> "$bytes B"
    }
}

fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return formatter.format(date)
}

fun handleFileAction(file: FileIndex, action: FileAction, viewModel: MainViewModel) {
    val safeDisplayName = "File_${file.name.hashCode().toString().takeLast(8)}"

    when (action) {
        FileAction.Upload -> {
            Timber.i("📤 Upload requested for file: $safeDisplayName")
            // Implement individual file upload
            viewModel.uploadSingleFile(file)
        }
        FileAction.Download -> {
            Timber.i("📥 Download requested for file: $safeDisplayName")
            // Implement individual file download
            viewModel.downloadSingleFile(file)
        }
        FileAction.Delete -> {
            Timber.i("🗑️ Delete requested for file: $safeDisplayName")
            // Implement file deletion
            viewModel.deleteSingleFile(file)
        }
        FileAction.Info -> {
            Timber.i("ℹ️ Info requested for file: $safeDisplayName")
            // Show file details
            showFileInfo(file)
        }
    }
}

// 🆕 ADD: Skeleton loading for files
@Composable
fun FileSkeletonItem() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skeleton icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Skeleton text
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                )
            }

            // Skeleton menu button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp)
                    )
            )
        }
    }
}

// 🆕 ADD: Loading state display
@Composable
fun FilesLoadingState() {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(6) { // Show 6 skeleton items
            FileSkeletonItem()
        }
    }
}


fun showFileInfo(file: FileIndex) {
    val safeDisplayName = "File_${file.name.hashCode().toString().takeLast(8)}"
    Timber.d("📋 File Info - Name: $safeDisplayName, Size: ${file.size}, Type: ${file.fileType}, Backed up: ${file.isBackedUp}")
    // TODO: Show dialog with file information
}
