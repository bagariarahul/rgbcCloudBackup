package com.rgbc.cloudBackup.core.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rgbc.cloudBackup.R
import com.rgbc.cloudBackup.core.domain.usecase.GetAllFilesUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadFileUseCase
import com.rgbc.cloudBackup.core.domain.usecase.CheckDuplicateFileUseCase
import com.rgbc.cloudBackup.core.domain.usecase.UploadProgress
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class AutoBackupService : Service() {

    @Inject
    lateinit var getAllFilesUseCase: GetAllFilesUseCase

    @Inject
    lateinit var uploadFileUseCase: UploadFileUseCase

    @Inject
    lateinit var checkDuplicateUseCase: CheckDuplicateFileUseCase

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backupJob: Job? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_AUTO_BACKUP = "start_auto_backup"
        const val ACTION_STOP_AUTO_BACKUP = "stop_auto_backup"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_AUTO_BACKUP -> {
                startAutoBackup()
            }
            ACTION_STOP_AUTO_BACKUP -> {
                stopAutoBackup()
            }
        }
        return START_STICKY
    }

    // 🆕 ADD: Permission check helper
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Pre-Android 13 doesn't need runtime permission
        }
    }

    private fun startAutoBackup() {
        Timber.d("🤖 Starting auto-backup service")

        // 🔧 FIX: Check permission before creating notification
        if (!hasNotificationPermission()) {
            Timber.w("⚠️ Notification permission not granted, starting service without notifications")
            // Start the service anyway, but without notifications
            startBackgroundWork()
            return
        }

        try {
            val notification = NotificationCompat.Builder(this, "backup_channel")
                .setContentTitle("Auto Backup Active")
                .setContentText("Monitoring for new files to backup")
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
            startBackgroundWork()

        } catch (e: SecurityException) {
            Timber.e(e, "🚫 Failed to show notification due to security exception")
            startBackgroundWork() // Continue without notifications
        }
    }

    private fun startBackgroundWork() {
        backupJob = serviceScope.launch {
            try {
                while (isActive) {
                    performAutoBackup()
                    delay(30_000) // Check every 30 seconds
                }
            } catch (e: CancellationException) {
                Timber.d("Auto-backup cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Auto-backup failed")
            }
        }
    }

    private suspend fun performAutoBackup() {
        try {
            Timber.d("🔄 Performing auto-backup check")

            // Get files that need backing up
            val allFiles = getAllFilesUseCase().first()
            val pendingFiles = allFiles.filter { !it.isBackedUp && it.shouldBackup }

            if (pendingFiles.isEmpty()) {
                Timber.d("✅ No files pending backup")
                return
            }

            Timber.d("📤 Auto-backup found ${pendingFiles.size} files to backup")

            // Update notification (with permission check)
            updateNotificationSafely("Backing up ${pendingFiles.size} files...")

            var successCount = 0
            var failCount = 0

            pendingFiles.take(5).forEach { file -> // Limit to 5 files per batch
                try {

                    // Check for duplicates
                    val duplicateCheck = checkDuplicateUseCase(file.path, file.name, file.size)
                    if (duplicateCheck.shouldSkip) {
                        Timber.d("⏭️ Skipping duplicate: ${file.name}")
                        return@forEach
                    }

                    // Perform upload
                    uploadFileUseCase.execute(File(file.path)).collect { progress ->
                        when (progress) {
                            is UploadProgress.Completed -> {
                                successCount++
                                Timber.d("✅ Auto-backup completed: ${file.name}")
                            }
                            is UploadProgress.Failed -> {
                                failCount++
                                Timber.e("❌ Auto-backup failed: ${progress.error}")
                            }
                            else -> {
                                // Progress updates
                            }
                        }
                    }
                } catch (e: Exception) {
                    failCount++
                    Timber.e(e, "Auto-backup error for: ${file.name}")
                }
            }

            // Show completion notification
            if (successCount > 0 || failCount > 0) {
                showCompletionNotificationSafely(successCount, failCount)
            }

            updateNotificationSafely("Monitoring for new files...")

        } catch (e: Exception) {
            Timber.e(e, "Auto-backup cycle failed")
        }
    }

    // 🆕 ADD: Safe notification update with permission check
    private fun updateNotificationSafely(message: String) {
        if (!hasNotificationPermission()) {
            Timber.d("📢 Would show notification: $message")
            return
        }

        try {
            val notification = NotificationCompat.Builder(this, "backup_channel")
                .setContentTitle("Auto Backup")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setOngoing(true)
                .build()

            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.e(e, "🚫 Failed to update notification")
        }
    }

    // 🆕 ADD: Safe completion notification with permission check
    private fun showCompletionNotificationSafely(successCount: Int, failCount: Int) {
        if (!hasNotificationPermission()) {
            Timber.d("📢 Backup complete: $successCount successful, $failCount failed")
            return
        }

        try {
            val message = if (failCount == 0) {
                "✅ Backed up $successCount files"
            } else {
                "⚠️ Backed up $successCount files, $failCount failed"
            }

            val notification = NotificationCompat.Builder(this, "backup_result_channel")
                .setContentTitle("Auto Backup Complete")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_notification)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID + 1, notification)
        } catch (e: SecurityException) {
            Timber.e(e, "🚫 Failed to show completion notification")
        }
    }

    private fun stopAutoBackup() {
        Timber.d("🛑 Stopping auto-backup service")
        backupJob?.cancel()

        try {
            stopForeground(true)
        } catch (e: Exception) {
            Timber.e(e, "Error stopping foreground service")
        }

        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
