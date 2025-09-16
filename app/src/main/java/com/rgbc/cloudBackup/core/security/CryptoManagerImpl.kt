package com.rgbc.cloudBackup.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManagerImpl @Inject constructor(
    @ApplicationContext  private val context: Context,
    private val auditLogger: SecurityAuditLogger
) : CryptoManager {

    private val keyAlias = "CloudBackupMasterKey"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    /**
     * Encrypts data using AES-256-GCM with Android Keystore
     */
    override fun encryptData(plaintext: ByteArray): EncryptedData {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv

            Timber.d("Data encrypted successfully, size: ${ciphertext.size} bytes")
            EncryptedData(ciphertext, iv)
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt data")
            throw CryptoException("Encryption failed", e)
        }
    }

    /**
     * Decrypts data using AES-256-GCM with Android Keystore
     */
    override fun decryptData(encryptedData: EncryptedData): ByteArray {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getOrCreateKey()
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plaintext = cipher.doFinal(encryptedData.ciphertext)

            Timber.d("Data decrypted successfully, size: ${plaintext.size} bytes")
            plaintext
        } catch (e: Exception) {
            Timber.e(e, "Failed to decrypt data")
            throw CryptoException("Decryption failed", e)
        }
    }

    /**
     * Encrypts a string and returns Base64 encoded result
     */
    override fun encryptString(plaintext: String): String {
        val encryptedData = encryptData(plaintext.toByteArray(Charsets.UTF_8))
        return encryptedData.toBase64String()
    }

    /**
     * Decrypts a Base64 encoded encrypted string
     */
    override fun decryptString(encryptedBase64: String): String {
        val encryptedData = EncryptedData.fromBase64String(encryptedBase64)
        val plaintext = decryptData(encryptedData)
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Creates encrypted shared preferences for storing sensitive data
     */
    override fun createEncryptedSharedPreferences(fileName: String): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create encrypted shared preferences")
            throw CryptoException("Failed to create encrypted shared preferences", e)
        }
    }

    /**
     * Encrypts a file and returns encrypted file data with metadata
     */
    suspend fun encryptFile(inputFile: File): EncryptedFileResult {
        return try {
            val startTime = System.currentTimeMillis()
            val safeFileName = "File_${inputFile.name.hashCode().toString().takeLast(8)}"

            auditLogger.logEncryptionStart(safeFileName, inputFile.length())
            Timber.i("Starting file encryption for: $safeFileName")

            // Read original file
            val plaintext = inputFile.readBytes()

            // Encrypt the data
            val encryptedData = encryptData(plaintext)

            // Create encrypted file with metadata
            val encryptedFile = EncryptedFile(
                originalFileName = inputFile.name,
                originalSize = inputFile.length(),
                encryptedData = encryptedData,
                encryptionAlgorithm = "AES-256-GCM",
                encryptionTimestamp = System.currentTimeMillis()
            )

            val encryptionDuration = System.currentTimeMillis() - startTime
            auditLogger.logEncryptionSuccess(safeFileName, encryptionDuration)

            EncryptedFileResult.Success(encryptedFile)

        } catch (e: Exception) {
            val safeFileName = "File_${inputFile.name.hashCode().toString().takeLast(8)}"
            auditLogger.logEncryptionFailure(safeFileName, e.message ?: "Unknown error")
            Timber.e(e, "File encryption failed for: $safeFileName")
            EncryptedFileResult.Error(e.message ?: "Encryption failed")
        }
    }

    /**
     * Decrypts an encrypted file and saves to specified location
     */
    suspend fun decryptFile(encryptedFile: EncryptedFile, outputFile: File): DecryptedFileResult {
        return try {
            val startTime = System.currentTimeMillis()
            val safeFileName = "File_${encryptedFile.originalFileName.hashCode().toString().takeLast(8)}"

            auditLogger.logDecryptionStart(safeFileName, encryptedFile.originalSize)
            Timber.i("Starting file decryption for: $safeFileName")

            // Decrypt the data
            val decryptedData = decryptData(encryptedFile.encryptedData)

            // Write decrypted data to output file
            outputFile.writeBytes(decryptedData)

            // Verify file integrity
            if (outputFile.length() != encryptedFile.originalSize) {
                throw Exception("File size mismatch after decryption")
            }

            val decryptionDuration = System.currentTimeMillis() - startTime
            auditLogger.logDecryptionSuccess(safeFileName, decryptionDuration)

            DecryptedFileResult.Success(outputFile.absolutePath)

        } catch (e: Exception) {
            val safeFileName = "File_${encryptedFile.originalFileName.hashCode().toString().takeLast(8)}"
            auditLogger.logDecryptionFailure(safeFileName, e.message ?: "Unknown error")
            Timber.e(e, "File decryption failed for: $safeFileName")
            DecryptedFileResult.Error(e.message ?: "Decryption failed")
        }
    }

    /**
     * Gets or creates the master encryption key in Android Keystore
     */
    private fun getOrCreateKey(): SecretKey {
        return if (keyStore.containsAlias(keyAlias)) {
            (keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            generateKey()
        }
    }

    /**
     * Generates a new AES-256 key in Android Keystore
     */
    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false) // Set to true for sensitive operations
            .build()

        keyGenerator.init(keyGenParameterSpec)
        val secretKey = keyGenerator.generateKey()
        Timber.d("New encryption key generated and stored in Android Keystore")
        return secretKey
    }
}

/**
 * Represents an encrypted file with metadata
 */
data class EncryptedFile(
    val originalFileName: String,
    val originalSize: Long,
    val encryptedData: EncryptedData,
    val encryptionAlgorithm: String,
    val encryptionTimestamp: Long
) {
    companion object {
        fun fromSerialized(serializedData: ByteArray): EncryptedFile {
            return try {
                Timber.d("🔧 Starting file deserialization: ${serializedData.size} bytes")

                if (serializedData.size < 4) {
                    throw Exception("Invalid file format: too small")
                }

                // Read header size (first 4 bytes)
                val headerSize = ((serializedData[0].toInt() and 0xFF) shl 24) or
                        ((serializedData[1].toInt() and 0xFF) shl 16) or
                        ((serializedData[2].toInt() and 0xFF) shl 8) or
                        (serializedData[3].toInt() and 0xFF)

                if (headerSize <= 0 || headerSize > serializedData.size - 4) {
                    throw Exception("Invalid header size: $headerSize")
                }

                // Read metadata JSON
                val metadataBytes = serializedData.sliceArray(4 until 4 + headerSize)
                val metadataJson = String(metadataBytes, Charsets.UTF_8)

                val gson = com.google.gson.Gson()
                val metadata = gson.fromJson(metadataJson, Map::class.java) as Map<String, Any>

                // Extract metadata
                val originalName = metadata["originalFileName"] as String
                val originalSize = (metadata["originalSize"] as Double).toLong()
                val algorithm = metadata["algorithm"] as String
                val timestamp = (metadata["timestamp"] as Double).toLong()
                val ivLength = (metadata["ivLength"] as Double).toInt()
                val dataLength = (metadata["dataLength"] as Double).toInt()

                // Validate sizes
                val expectedTotalSize = 4 + headerSize + ivLength + dataLength
                if (serializedData.size != expectedTotalSize) {
                    throw Exception("Size mismatch: expected $expectedTotalSize, got ${serializedData.size}")
                }

                // Extract IV and ciphertext
                var offset = 4 + headerSize
                val iv = serializedData.sliceArray(offset until offset + ivLength)
                offset += ivLength
                val ciphertext = serializedData.sliceArray(offset until offset + dataLength)

                Timber.d("✅ File deserialized successfully: $originalName, $originalSize bytes")

                EncryptedFile(
                    originalFileName = originalName,
                    originalSize = originalSize,
                    encryptedData = EncryptedData(ciphertext, iv),
                    encryptionAlgorithm = algorithm,
                    encryptionTimestamp = timestamp
                )

            } catch (e: Exception) {
                Timber.e(e, "❌ File deserialization failed")
                throw Exception("Deserialization failed: ${e.message}")
            }
        }
    }

}

sealed class EncryptedFileResult {
    data class Success(val encryptedFile: EncryptedFile) : EncryptedFileResult()
    data class Error(val message: String) : EncryptedFileResult()
}

sealed class DecryptedFileResult {
    data class Success(val filePath: String) : DecryptedFileResult()
    data class Error(val message: String) : DecryptedFileResult()
}
