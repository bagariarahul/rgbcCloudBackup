package com.rgbc.cloudBackup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CloudBackupApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize SQLCipher native library
        initializeSQLCipher()

        // Initialize Timber logger
        initializeLogging()

        // 🆕 ADD: Create notification channels
        createNotificationChannels()

        Timber.i("CloudBackup Application started with notification channels")
    }

    private fun initializeSQLCipher() {
        try {
            System.loadLibrary("sqlcipher")
            Timber.d("SQLCipher native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load SQLCipher native library")
            throw RuntimeException("SQLCipher initialization failed", e)
        }
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    // 🆕 ADD: Create notification channels for Android 8.0+
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Auto backup ongoing notification channel
                val backupChannel = NotificationChannel(
                    "backup_channel",
                    "Auto Backup Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows when auto backup service is actively running"
                    setShowBadge(false)
                }

                // Backup completion notification channel
                val resultChannel = NotificationChannel(
                    "backup_result_channel",
                    "Backup Results",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Shows backup completion status and results"
                    setShowBadge(true)
                }

                // Create the channels
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(backupChannel)
                notificationManager.createNotificationChannel(resultChannel)

                Timber.d("📢 Notification channels created successfully")

            } catch (e: Exception) {
                Timber.e(e, "❌ Failed to create notification channels")
            }
        } else {
            Timber.d("📢 Notification channels not needed (Android < 8.0)")
        }
    }
}
