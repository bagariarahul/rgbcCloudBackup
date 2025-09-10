package com.rgbc.cloudBackup

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
import com.rgbc.cloudBackup.core.auth.AuthState
import com.rgbc.cloudBackup.features.auth.presentation.AuthScreen
import com.rgbc.cloudBackup.features.auth.presentation.AuthViewModel
import com.rgbc.cloudBackup.features.main.presentation.MainScreen
import com.rgbc.cloudBackup.ui.theme.CloudBackupTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

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
                    DebugApp()
                }
            }
        }
    }
}

@Composable
fun DebugApp() {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf("debug") }

    // Log the current state
    LaunchedEffect(authState) {
        Timber.d("🔍 AuthState changed: $authState")
        when (authState) {
            is AuthState.Authenticated -> {
                Timber.d("✅ User is authenticated: ${(authState as AuthState.Authenticated).user.email}")
                currentScreen = "main"
            }
            is AuthState.Unauthenticated -> {
                Timber.d("❌ User is not authenticated")
                currentScreen = "auth"
            }
            is AuthState.Loading -> {
                Timber.d("⏳ Authentication is loading...")
                currentScreen = "loading"
            }
            is AuthState.Error -> {
                Timber.d("🚨 Authentication error: ${(authState as AuthState.Error).message}")
                currentScreen = "auth"
            }
        }
    }

    // Show different screens based on current state
    when (currentScreen) {
        "loading" -> {
            LoadingScreen(authState = authState)
        }
        "auth" -> {
            AuthScreen(
                onNavigateToMain = {
                    Timber.d("🚀 Navigating to main screen")
                    currentScreen = "main"
                }
            )
        }
        "main" -> {
            MainScreen()
        }
        "debug" -> {
            DebugScreen(
                authState = authState,
                onForceAuth = { currentScreen = "auth" },
                onForceMain = { currentScreen = "main" }
            )
        }
    }
}

@Composable
fun DebugScreen(
    authState: AuthState,
    onForceAuth: () -> Unit,
    onForceMain: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔍 Debug Screen",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Current Auth State:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (authState) {
                        is AuthState.Loading -> "⏳ Loading..."
                        is AuthState.Unauthenticated -> "❌ Not authenticated"
                        is AuthState.Authenticated -> "✅ Authenticated: ${authState.user.email}"
                        is AuthState.Error -> "🚨 Error: ${authState.message}"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onForceAuth,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔑 Show Auth Screen")
            }

            Button(
                onClick = onForceMain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🏠 Show Main Screen")
            }
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