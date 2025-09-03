package com.rgbc.cloudBackup.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoManagerImpl @Inject constructor(
    private val context: Context
)   : CryptoManager {
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

    /**
     * Creates encrypted shared preferences for storing sensitive data
     */
    override fun createEncryptedSharedPreferences(fileName: String) = try {
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
