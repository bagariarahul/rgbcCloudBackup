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
 * SyncPullScheduler — Schedules the periodic SyncPullWorker.
 *
 * Sprint 2.5: Mirrors BackupScheduler but for the PULL direction.
 * Runs every 15 minutes by default to check the Master for new files.
 *
 * Uses UPDATE policy so interval/constraint changes take effect immediately.
 */
object SyncPullScheduler {

    fun schedulePeriodic(
        context: Context,
        intervalMinutes: Long = 15,
        wifiOnly: Boolean = false
    ) {
        Timber.i("📥 Scheduling periodic pull sync (every ${intervalMinutes}min, wifiOnly=$wifiOnly)")

        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SyncPullWorker>(
            repeatInterval = intervalMinutes,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
            flexTimeInterval = 5,
            flexTimeIntervalUnit = TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag("sync_pull")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                SyncPullWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )

        Timber.i("📥 Pull sync worker enqueued: ${SyncPullWorker.WORK_NAME}")
    }

    fun cancelAll(context: Context) {
        Timber.i("📥 Cancelling pull sync worker")
        WorkManager.getInstance(context)
            .cancelUniqueWork(SyncPullWorker.WORK_NAME)
    }
}