package com.rgbc.cloudBackup.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityAuditLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val auditLogFile = File(context.filesDir, "security_audit.log")

    suspend fun logUploadStart(safeFileName: String, fileSize: Long) {
        logSecurityEvent("UPLOAD_START", mapOf(
            "file" to safeFileName,
            "size" to fileSize.toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logUploadSuccess(safeFileName: String, uploadDuration: Long) {
        logSecurityEvent("UPLOAD_SUCCESS", mapOf(
            "file" to safeFileName,
            "duration_ms" to uploadDuration.toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logUploadFailure(safeFileName: String, error: String) {
        logSecurityEvent("UPLOAD_FAILURE", mapOf(
            "file" to safeFileName,
            "error" to error,
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logFileCreation(safeFileName: String, filePath: String) {
        logSecurityEvent("FILE_CREATED", mapOf(
            "file" to safeFileName,
            "path_hash" to filePath.hashCode().toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    private suspend fun logSecurityEvent(eventType: String, details: Map<String, String>) {
        withContext(Dispatchers.IO) {
            try {
                val logEntry = buildString {
                    append("[${getCurrentTimestamp()}] ")
                    append("[$eventType] ")
                    details.forEach { (key, value) ->
                        append("$key=$value ")
                    }
                    appendLine()
                }

                // Log to Timber (console/logcat)
                Timber.i("SECURITY_AUDIT: $logEntry")

                // Log to persistent file
                auditLogFile.appendText(logEntry)

                // Rotate log file if too large (>10MB)
                if (auditLogFile.length() > 10 * 1024 * 1024) {
                    rotateLogFile()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to write security audit log")
            }
        }
    }

    private fun rotateLogFile() {
        try {
            val backupFile = File(context.filesDir, "security_audit_backup.log")
            if (backupFile.exists()) backupFile.delete()
            auditLogFile.renameTo(backupFile)
            auditLogFile.createNewFile()
            Timber.i("Security audit log rotated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to rotate audit log")
        }
    }

    private fun getCurrentTimestamp(): String {
        return dateFormat.format(Date())
    }
    suspend fun logDownloadStart(safeFileName: String, fileSize: Long) {
        logSecurityEvent("DOWNLOAD_START", mapOf(
            "file" to safeFileName,
            "size" to fileSize.toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logDownloadSuccess(safeFileName: String, downloadDuration: Long) {
        logSecurityEvent("DOWNLOAD_SUCCESS", mapOf(
            "file" to safeFileName,
            "duration_ms" to downloadDuration.toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logDownloadFailure(safeFileName: String, error: String) {
        logSecurityEvent("DOWNLOAD_FAILURE", mapOf(
            "file" to safeFileName,
            "error" to error,
            "timestamp" to getCurrentTimestamp()
        ))
    }
    suspend fun logEncryptionStart(safeFileName: String, fileSize: Long) {
        logSecurityEvent("ENCRYPTION_START", mapOf(
            "file" to safeFileName,
            "size" to fileSize.toString(),
            "algorithm" to "AES-256-GCM",
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logEncryptionSuccess(safeFileName: String, encryptionDuration: Long) {
        logSecurityEvent("ENCRYPTION_SUCCESS", mapOf(
            "file" to safeFileName,
            "duration_ms" to encryptionDuration.toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logEncryptionFailure(safeFileName: String, error: String) {
        logSecurityEvent("ENCRYPTION_FAILURE", mapOf(
            "file" to safeFileName,
            "error" to error,
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logDecryptionStart(safeFileName: String, fileSize: Long) {
        logSecurityEvent("DECRYPTION_START", mapOf(
            "file" to safeFileName,
            "size" to fileSize.toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logDecryptionSuccess(safeFileName: String, decryptionDuration: Long) {
        logSecurityEvent("DECRYPTION_SUCCESS", mapOf(
            "file" to safeFileName,
            "duration_ms" to decryptionDuration.toString(),
            "timestamp" to getCurrentTimestamp()
        ))
    }

    suspend fun logDecryptionFailure(safeFileName: String, error: String) {
        logSecurityEvent("DECRYPTION_FAILURE", mapOf(
            "file" to safeFileName,
            "error" to error,
            "timestamp" to getCurrentTimestamp()
        ))
    }


}
