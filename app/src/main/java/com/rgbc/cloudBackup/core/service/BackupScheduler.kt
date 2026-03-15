package com.rgbc.cloudBackup.core.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * BackupScheduler — single entry point for managing the periodic backup worker.
 */
object BackupScheduler {

    /**
     * Schedule the periodic backup worker.
     *
     * @param context Application context
     * @param intervalHours How often to run (minimum 15 minutes per WorkManager). Default: 1 hour.
     * @param wifiOnly If true, requires UNMETERED (WiFi). If false, any CONNECTED network works.
     */
    fun schedulePeriodic(
        context: Context,
        intervalHours: Long = 1,
        wifiOnly: Boolean = false
    ) {
        Timber.i("⏰ Scheduling periodic backup (every ${intervalHours}h, wifiOnly=$wifiOnly)")

        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            repeatInterval = intervalHours,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = 15,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("backup")
            .build()

        // UPDATE policy so constraint/interval changes take effect immediately.
        // KEEP would silently ignore new parameters if a worker already exists.
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                BackupWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

        Timber.i("⏰ Periodic backup worker enqueued: ${BackupWorker.WORK_NAME}")
    }

    /**
     * Cancel the periodic backup worker.
     */
    fun cancelAll(context: Context) {
        Timber.i("⏰ Cancelling periodic backup worker")
        WorkManager.getInstance(context)
            .cancelUniqueWork(BackupWorker.WORK_NAME)
    }
}