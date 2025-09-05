package com.rgbc.cloudBackup

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.rgbc.cloudBackup.features.main.presentation.MainScreen  // ← Import the separate MainScreen
import com.rgbc.cloudBackup.ui.theme.CloudBackupTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("MainActivity onCreate started")
        enableEdgeToEdge()
        setContent {
            CloudBackupTheme {
                Timber.d("Setting MainScreen content")
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(  // ← Now this calls the MainScreen from MainScreen.kt
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Remove all the other functions (MainScreen, StatsCard, FileItem, etc.) from here!
