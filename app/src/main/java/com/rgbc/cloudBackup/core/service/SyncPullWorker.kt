package com.rgbc.cloudBackup.core.service

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.rgbc.cloudBackup.R
import com.rgbc.cloudBackup.core.data.database.entity.FileIndex
import com.rgbc.cloudBackup.core.data.database.entity.FileIndexDao
import com.rgbc.cloudBackup.core.network.api.BackupApiService
import com.rgbc.cloudBackup.core.network.api.RemoteFile
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.util.Date

/**
 * SyncPullWorker — Pulls new files from the Master Node to this Android device.
 *
 * Sprint 2.5: Completes the bidirectional sync loop.
 *   Push path: Android → Master (handled by BackupWorker)
 *   Pull path: Master → Android (handled by THIS worker)
 *
 * Pipeline:
 *   1. GET /api/files/list (auto-redirected to Master by OkHttp interceptor)
 *   2. For each remote file:
 *      a. Check Room by checksum — if exists, skip (already synced)
 *      b. Download via GET /api/files/download/{file_id}
 *      c. SHA-256 verify during write (single-pass)
 *      d. Insert into Room with syncDirection = "PULL"
 *   3. Files marked PULL are excluded from BackupWorker's upload queue
 *      (via the updated getFilesToBackup() DAO query)
 *
 * Storage: Downloaded files are saved to the app's external files directory
 *   at context.getExternalFilesDir("RGBCSync")
 */
@HiltWorker
class SyncPullWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: BackupApiService,
    private val fileIndexDao: FileIndexDao
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "sync_pull_worker"
        private const val NOTIFICATION_ID = 2002
    }

    override suspend fun doWork(): Result {
        Timber.i("📥 SyncPullWorker started (attempt ${runAttemptCount + 1})")

        return try {
            setForeground(createForegroundInfo("Checking for new files on Master..."))

            // 1. Fetch the remote file manifest from the Master
            val remoteFiles = fetchAllRemoteFiles()

            if (remoteFiles.isEmpty()) {
                Timber.i("📥 SyncPullWorker: No remote files (Master offline or empty)")
                return Result.success()
            }

            Timber.i("📥 SyncPullWorker: ${remoteFiles.size} files on Master")

            // 2. Filter to files we don't have locally
            val newFiles = filterNewFiles(remoteFiles)

            if (newFiles.isEmpty()) {
                Timber.i("📥 SyncPullWorker: All files already synced. Done.")
                return Result.success()
            }

            Timber.i("📥 SyncPullWorker: ${newFiles.size} new files to download")

            // 3. Download each new file
            var successCount = 0
            var failCount = 0

            for ((index, remoteFile) in newFiles.withIndex()) {
                setForeground(
                    createForegroundInfo("Downloading ${index + 1} of ${newFiles.size}...")
                )

                try {
                    val downloaded = downloadAndIndex(remoteFile)
                    if (downloaded) successCount++ else failCount++
                } catch (e: Exception) {
                    Timber.e(e, "📥 SyncPullWorker: Error downloading ${remoteFile.originalName}")
                    failCount++
                }
            }

            Timber.i("📥 SyncPullWorker complete: $successCount downloaded, $failCount failed")

            when {
                failCount == 0 -> Result.success()
                successCount > 0 -> Result.success() // Partial success
                runAttemptCount < 2 -> Result.retry()
                else -> Result.failure()
            }

        } catch (e: Exception) {
            Timber.e(e, "📥 SyncPullWorker: Fatal error")
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    /**
     * Fetch all files from the Master via paginated API calls.
     * The MasterRedirectInterceptor transparently routes these to
     * the Master's Cloudflare Tunnel URL.
     */
    private suspend fun fetchAllRemoteFiles(): List<RemoteFile> {
        val allFiles = mutableListOf<RemoteFile>()
        var offset = 0
        val limit = 100

        while (true) {
            try {
                val response = apiService.getFileList(limit = limit, offset = offset)

                if (!response.isSuccessful || response.body() == null) {
                    Timber.w("📥 File list failed: HTTP ${response.code()}")
                    break
                }

                val body = response.body()!!
                allFiles.addAll(body.files)

                val pagination = body.pagination
                val total = pagination?.total ?: body.files.size
                offset += limit

                if (offset >= total || body.files.isEmpty()) break

            } catch (e: Exception) {
                Timber.e(e, "📥 File list request error")
                break
            }
        }

        return allFiles
    }

    /**
     * Filter remote files to only those we don't already have locally.
     * Uses checksum-based deduplication — if the checksum exists in Room,
     * we skip it regardless of filename.
     */
    private suspend fun filterNewFiles(remoteFiles: List<RemoteFile>): List<RemoteFile> {
        return remoteFiles.filter { remote ->
            val checksum = remote.checksum
            if (checksum.isNullOrBlank()) {
                // No checksum from server — can't deduplicate, download it
                true
            } else {
                // Check if we already have this exact content
                val count = fileIndexDao.countByChecksum(checksum)
                count == 0
            }
        }
    }

    /**
     * Download a single file from the Master and index it in Room.
     * Returns true on success.
     */
    private suspend fun downloadAndIndex(remoteFile: RemoteFile): Boolean = withContext(Dispatchers.IO) {
        val fileName = remoteFile.originalName
        val fileId = remoteFile.relativePath ?: remoteFile.id

        Timber.i("📥 Downloading: $fileName (${remoteFile.fileSize} bytes)")

        // Determine download destination
        val syncDir = appContext.getExternalFilesDir("RGBCSync")
            ?: return@withContext false.also {
                Timber.e("📥 External files directory unavailable")
            }

        syncDir.mkdirs()

        var destFile = File(syncDir, fileName)

        // Handle name conflicts: add suffix if file exists with different content
        if (destFile.exists()) {
            val existingChecksum = computeLocalSha256(destFile)
            if (existingChecksum == remoteFile.checksum) {
                // Same content already on disk — just index it
                Timber.d("📥 File already on disk with matching checksum: $fileName")
                indexPulledFile(destFile, remoteFile)
                return@withContext true
            }
            // Different content — rename to avoid overwriting
            val base = fileName.substringBeforeLast(".", fileName)
            val ext = if (fileName.contains(".")) ".${fileName.substringAfterLast(".")}" else ""
            destFile = File(syncDir, "${base}_master${ext}")
            Timber.w("📥 Name conflict — downloading as: ${destFile.name}")
        }

        // Download via streaming
        try {
            val response = apiService.downloadFileById(fileId)

            if (!response.isSuccessful || response.body() == null) {
                Timber.e("📥 Download failed: HTTP ${response.code()} for $fileName")
                return@withContext false
            }

            // Stream to disk + compute SHA-256 simultaneously
            val downloadedChecksum = streamToDisk(response.body()!!, destFile)

            if (downloadedChecksum == null) {
                Timber.e("📥 Stream-to-disk failed for $fileName")
                destFile.delete()
                return@withContext false
            }

            // Verify checksum if server provided one
            if (!remoteFile.checksum.isNullOrBlank() &&
                downloadedChecksum != remoteFile.checksum
            ) {
                Timber.e(
                    "📥 Checksum mismatch for $fileName!\n" +
                            "   Expected: ${remoteFile.checksum}\n" +
                            "   Got:      $downloadedChecksum"
                )
                destFile.delete()
                return@withContext false
            }

            // Index in Room
            indexPulledFile(destFile, remoteFile, checksumOverride = downloadedChecksum)

            Timber.i("📥 ✅ Downloaded: ${destFile.name} (${destFile.length()} bytes)")
            return@withContext true

        } catch (e: Exception) {
            Timber.e(e, "📥 Download exception for $fileName")
            if (destFile.exists()) destFile.delete()
            return@withContext false
        }
    }

    /**
     * Stream a ResponseBody to a local file while computing SHA-256.
     * Single-pass: no double I/O.
     */
    private fun streamToDisk(body: ResponseBody, destFile: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                    }
                }
            }

            if (totalBytes == 0L) {
                Timber.w("📥 Downloaded file is empty: ${destFile.name}")
                return null
            }

            digest.digest().joinToString("") { "%02x".format(it) }

        } catch (e: Exception) {
            Timber.e(e, "📥 Stream-to-disk error")
            null
        }
    }

    /**
     * Insert or update a pulled file in the Room database.
     * Marks it with syncDirection = "PULL" so BackupWorker skips it.
     */
    private suspend fun indexPulledFile(
        localFile: File,
        remoteFile: RemoteFile,
        checksumOverride: String? = null
    ) {
        val checksum = checksumOverride
            ?: remoteFile.checksum
            ?: computeLocalSha256(localFile)
            ?: "unknown"

        val now = Date()
        val ext = localFile.extension.lowercase()

        val fileIndex = FileIndex(
            name = localFile.name,
            path = localFile.absolutePath,
            size = localFile.length(),
            checksum = checksum,
            createdAt = now,
            modifiedAt = now,
            shouldBackup = true,
            isBackedUp = true,              // Already on the Master — no need to re-upload
            backedUpAt = now,
            fileType = ext,
            mimeType = guessMimeType(ext),
            syncDirection = "PULL"          // Key: prevents re-upload
        )

        fileIndexDao.insertFile(fileIndex)
    }

    private fun computeLocalSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(65536)
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun guessMimeType(ext: String): String = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "pdf" -> "application/pdf"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(appContext, "backup_channel")
            .setContentTitle("RGBC Drive — Syncing")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}