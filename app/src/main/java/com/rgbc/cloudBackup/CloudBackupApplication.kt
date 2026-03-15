package com.rgbc.cloudBackup

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.rgbc.cloudBackup.core.service.BackupScheduler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CloudBackupApplication : Application(), Configuration.Provider {

    // ── Hilt WorkManager integration ────────────────────────────────────
    // HiltWorkerFactory creates Worker instances with @AssistedInject
    // dependencies. This is injected by Hilt automatically.
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.INFO
            )
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize SQLCipher native library
        initializeSQLCipher()

        // Initialize Timber logger
        initializeLogging()

        // Create notification channels (required for Android 8.0+)
        createNotificationChannels()

        // ── Schedule periodic backup worker ─────────────────────────────
        // Safe to call on every launch — KEEP policy means it won't
        // duplicate an already-enqueued worker.
        BackupScheduler.schedulePeriodic(this, intervalHours = 1)

        Timber.i("CloudBackup Application started with WorkManager backup scheduler")
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

    // Create notification channels for Android 8.0+
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