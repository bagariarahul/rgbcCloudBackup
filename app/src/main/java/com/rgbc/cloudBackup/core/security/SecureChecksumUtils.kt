package com.rgbc.cloudBackup.core.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureChecksumUtils @Inject constructor() {

    companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val SHA256 = "SHA-256"
    }

    /**
     * Calculates SHA-256 checksum of a file.
     */
    suspend fun calculateSHA256(file: File): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance(SHA256)
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "Checksum failed for ${file.absolutePath}")
            throw ChecksumException("Failed to calculate SHA-256", e)
        }
    }

    /**
     * Verifies a file’s checksum.
     */
    suspend fun verifyChecksum(file: File, expected: String): Boolean {
        return try {
            val actual = calculateSHA256(file)
            actual.equals(expected, ignoreCase = true).also {
                Timber.d("Checksum verify: expected=$expected actual=$actual result=$it")
            }
        } catch (e: Exception) {
            Timber.e(e, "Verification error")
            false
        }
    }
}
