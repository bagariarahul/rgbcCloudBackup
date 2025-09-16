package com.rgbc.cloudBackup.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import timber.log.Timber

import androidx.compose.material.icons.filled.Cloud

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Files : Screen("files", "Files", Icons.Default.Folder)
    object Directories : Screen("directories", "Folders", Icons.Default.FolderSpecial)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object ServerSync : Screen("server_sync", "Server", Icons.Default.Cloud) // 🆕
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Files,
    Screen.Directories,
    Screen.Settings,
    Screen.ServerSync // 🆕
)
.also {
    Timber.d("Bottom navigation items configured: ${it.size} screens")
}
