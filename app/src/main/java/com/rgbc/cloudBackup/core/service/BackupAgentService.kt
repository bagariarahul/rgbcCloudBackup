//package com.rgbc.cloudBackup.core.service
//
//import android.app.Service
//import android.content.Intent
//import android.os.IBinder
//import androidx.work.*
//import com.rgbc.cloudBackup.core.data.database.entity.BackupQueue
//import com.rgbc.cloudBackup.core.data.database.entity.BackupQueueDao
//import com.rgbc.cloudBackup.core.data.database.entity.BackupStatus
//import com.rgbc.cloudBackup.core.domain.usecase.UploadFileUseCase
//import com.rgbc.cloudBackup.core.domain.usecase.UploadProgress
//import com.rgbc.cloudBackup.core.preferences.SettingsManager
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.*
//import kotlinx.coroutines.flow.firstOrNull
//import timber.log.Timber
//import java.io.File
//import java.util.*
//import java.util.concurrent.TimeUnit
//import javax.inject.Inject
//
//@AndroidEntryPoint
//class BackupAgentService : Service() {
//
//    @Inject lateinit var backupQueueDao: BackupQueueDao
//    @Inject lateinit var uploadFileUseCase: UploadFileUseCase
//    @Inject lateinit var settingsManager: SettingsManager
//
//    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//    private var isProcessing = false
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        Timber.d("🤖 BackupAgent service started")
//
//        when (intent?.action) {
//            ACTION_START_PROCESSING -> startProcessing()
//            ACTION_STOP_PROCESSING -> stopProcessing()
//            ACTION_PROCESS_SINGLE -> {
//                val queueId = intent.getLongExtra(EXTRA_QUEUE_ID, -1)
//                if (queueId != -1L) processSingleItem(queueId)
//            }
//            ACTION_RETRY_FAILED -> retryFailedItems()
//        }
//
//        return START_STICKY
//    }
//
//    private fun startProcessing() {
//        if (isProcessing) return
//
//        isProcessing = true
//        Timber.i("🤖 Starting backup queue processing")
//
//        serviceScope.launch {
//            while (isProcessing) {
//                try {
//                    processQueue()
//                    delay(30_000) // Check queue every 30 seconds
//                } catch (e: Exception) {
//                    Timber.e(e, "🤖 Error in processing loop")
//                    delay(60_000) // Wait longer on error
//                }
//            }
//        }
//    }
//
//    private fun stopProcessing() {
//        isProcessing = false
//        Timber.i("🤖 Stopping backup queue processing")
//    }
//
//    private suspend fun processQueue() {
//        // Get settings
//        val maxConcurrent = settingsManager.maxConcurrentUploads.firstOrNull() ?: 2
//
//        // Get pending items
//        val pendingItems = backupQueueDao.getPendingItems(maxConcurrent)
//
//        if (pendingItems.isEmpty()) {
//            // Check for retryable failed items
//            val retryableItems = backupQueueDao.getRetryableItems(Date(), maxConcurrent)
//            processItems(retryableItems)
//        } else {
//            processItems(pendingItems)
//        }
//    }
//
//    private suspend fun processItems(items: List<BackupQueue>) {
//        val jobs = items.map { item ->
//            serviceScope.async {
//                processQueueItem(item)
//            }
//        }
//
//        // Wait for all uploads to complete
//        jobs.awaitAll()
//    }
//
//    private suspend fun processQueueItem(queueItem: BackupQueue) {
//        try {
//            Timber.d("🤖 Processing queue item: ${queueItem.originalName}")
//
//            // Update status to processing
//            backupQueueDao.updateStatus(queueItem.id, BackupStatus.PROCESSING)
//
//            // Check if file still exists
//            val file = File(queueItem.filePath)
//            if (!file.exists()) {
//                backupQueueDao.updateFailure(
//                    queueItem.id,
//                    BackupStatus.FAILED,
//                    "File not found: ${queueItem.filePath}",
//                    null
//                )
//                return
//            }
//
//            // Perform upload
//            uploadFileUseCase.execute(file).collect { progress ->
//                when (progress) {
//                    is UploadProgress.Completed -> {
//                        // Upload successful
//                        backupQueueDao.updateCompleted(
//                            queueItem.id,
//                            progress.serverId
//                        )
//                        Timber.i("🤖 ✅ Upload completed: ${progress.serverFileName}")
//                    }
//                    is UploadProgress.Failed -> {
//                        // Upload failed
//                        val shouldRetry = queueItem.retryCount < queueItem.maxRetries
//                        val nextRetry = if (shouldRetry) {
//                            Date(System.currentTimeMillis() + getRetryDelay(queueItem.retryCount))
//                        } else null
//
//                        val status = if (shouldRetry) BackupStatus.FAILED else BackupStatus.FAILED
//
//                        backupQueueDao.updateFailure(
//                            queueItem.id,
//                            status,
//                            progress.error,
//                            nextRetry
//                        )
//                        Timber.w("🤖 ❌ Upload failed: ${progress.error}")
//                    }
//                    else -> {
//                        // Progress updates - could be logged or stored
//                        Timber.v("🤖 Upload progress: $progress")
//                    }
//                }
//            }
//
//        } catch (e: Exception) {
//            Timber.e(e, "🤖 Exception processing queue item: ${queueItem.originalName}")
//            backupQueueDao.updateFailure(
//                queueItem.id,
//                BackupStatus.FAILED,
//                "Processing exception: ${e.message}",
//                if (queueItem.retryCount < queueItem.maxRetries) {
//                    Date(System.currentTimeMillis() + getRetryDelay(queueItem.retryCount))
//                } else null
//            )
//        }
//    }
//
//    private fun processSingleItem(queueId: Long) {
//        serviceScope.launch {
//            try {
//                val item = backupQueueDao.getByFilePath("") // You'd need to modify this
//                item?.let { processQueueItem(it) }
//            } catch (e: Exception) {
//                Timber.e(e, "Failed to process single item: $queueId")
//            }
//        }
//    }
//
//    private fun retryFailedItems() {
//        serviceScope.launch {
//            val retryableItems = backupQueueDao.getRetryableItems(Date(), 10)
//            processItems(retryableItems)
//        }
//    }
//
//    private fun getRetryDelay(retryCount: Int): Long {
//        // Exponential backoff: 1min, 5min, 15min
//        return when (retryCount) {
//            0 -> 1 * 60 * 1000L      // 1 minute
//            1 -> 5 * 60 * 1000L      // 5 minutes
//            2 -> 15 * 60 * 1000L     // 15 minutes
//            else -> 60 * 60 * 1000L  // 1 hour
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        isProcessing = false
//        serviceScope.cancel()
//        Timber.d("🤖 BackupAgent service destroyed")
//    }
//
//    companion object {
//        const val ACTION_START_PROCESSING = "START_PROCESSING"
//        const val ACTION_STOP_PROCESSING = "STOP_PROCESSING"
//        const val ACTION_PROCESS_SINGLE = "PROCESS_SINGLE"
//        const val ACTION_RETRY_FAILED = "RETRY_FAILED"
//        const val EXTRA_QUEUE_ID = "queue_id"
//    }
//}