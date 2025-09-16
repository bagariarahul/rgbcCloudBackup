package com.rgbc.cloudBackup

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.rgbc.cloudBackup.core.auth.AuthState
import com.rgbc.cloudBackup.features.auth.presentation.AuthScreen
import com.rgbc.cloudBackup.features.auth.presentation.AuthViewModel
import com.rgbc.cloudBackup.features.directory.presentation.DirectorySelectionScreen
import com.rgbc.cloudBackup.features.main.presentation.MainScreen
import com.rgbc.cloudBackup.ui.theme.CloudBackupTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.*
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.rgbc.cloudBackup.core.navigation.Screen
import com.rgbc.cloudBackup.core.navigation.bottomNavItems
import com.rgbc.cloudBackup.features.files.presentation.FilesScreen
import com.rgbc.cloudBackup.features.settings.presentation.SettingsScreen
import com.rgbc.cloudBackup.features.sync.presentation.ServerSyncScreen




@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        setContent {
            CloudBackupTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CloudBackupApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CloudBackupApp() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // Permission Handler
    PermissionHandler()

    // Log the current state
    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            Timber.d("🧭 Navigation destination changed to: ${destination.route}")
        }
    }

    // Navigation based on auth state
    when (authState) {
        is AuthState.Loading -> {
            LoadingScreen(authState = authState)
        }

        is AuthState.Unauthenticated, is AuthState.Error -> {
            AuthScreen(
                onNavigateToMain = {
                    Timber.d("🚀 Navigating to main screen")
                    navController.navigate("main") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }

        is AuthState.Authenticated -> {
            Timber.d("✅ User authenticated, showing main app with navigation")
            // Main app with bottom navigation
            Scaffold(
                bottomBar = {
                    CloudBackupBottomNav(navController = navController)
                }
            ) { paddingValues ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.padding(paddingValues)
                ) {
                    composable(Screen.Home.route) {
                        Timber.d("📱 Navigated to Home screen")
                        MainScreen(
                            onNavigateToAuth = {
                                Timber.d("🔐 Logging out from main screen")
                                authViewModel.logout()
                            },
                            onNavigateToDirectories = {
                                Timber.d("📁 Navigating to directories")
                                navController.navigate(Screen.Directories.route)
                            }
                        )
                    }

                    composable(Screen.Files.route) {
                        Timber.d("📄 Navigated to Files screen")
                        FilesScreen()
                    }

                    composable(Screen.Directories.route) {
                        Timber.d("📂 Navigated to Directories screen")
                        DirectorySelectionScreen(
                            onNavigateBack = {
                                Timber.d("⬅️ Navigating back from directories")
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(Screen.Settings.route) {
                        Timber.d("⚙️ Navigated to Settings screen")
                        SettingsScreen()
                    }

                    composable(Screen.ServerSync.route) {
                        Timber.d("🌐 Navigated to Server Sync screen")
                        ServerSyncScreen()
                    }


                }
            }
        }
    }
}

@Composable
fun CloudBackupBottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Timber.d("🧭 Bottom nav rendered, current route: $currentRoute")

    NavigationBar {
        bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route

            NavigationBarItem(
                icon = {
                    Icon(screen.icon, contentDescription = screen.title)
                },
                label = { Text(screen.title) },
                selected = isSelected,
                onClick = {
                    Timber.d("🔄 Navigation clicked: ${screen.title} → ${screen.route}")
                    Timber.d("🧭 Current route: $currentRoute")

                    if (currentRoute != screen.route) {
                        try {
                            // 🔧 FIX: Clear back stack and navigate properly
                            navController.navigate(screen.route) {
                                // Clear everything and go to destination
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = false // Don't save state to avoid corruption
                                }
                                launchSingleTop = true
                                restoreState = false // Don't restore state to avoid corruption
                            }
                            Timber.d("✅ Navigation successful to ${screen.route}")
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Navigation failed to ${screen.route}")
                            // 🔧 FIX: Fallback navigation
                            try {
                                navController.popBackStack()
                                navController.navigate(screen.route)
                                Timber.d("✅ Fallback navigation successful")
                            } catch (e2: Exception) {
                                Timber.e(e2, "❌ Fallback navigation also failed")
                            }
                        }
                    } else {
                        Timber.d("🔄 Already on ${screen.route}")
                    }
                }
            )
        }
    }
}




@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler() {
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    )

    if (!permissionsState.allPermissionsGranted) {
        LaunchedEffect(Unit) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
}

@Composable
fun LoadingScreen(authState: AuthState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "State: $authState",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}