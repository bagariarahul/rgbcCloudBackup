// app/src/main/java/com/rgbc/cloudBackup/core/security/CryptoManager.kt
package com.rgbc.cloudBackup.core.security

import android.content.SharedPreferences

/**
 * Encrypt/decrypt data and strings.
 */
interface CryptoManager {
    fun encryptData(plaintext: ByteArray): EncryptedData
    fun decryptData(encryptedData: EncryptedData): ByteArray

    fun encryptString(plaintext: String): String
    fun decryptString(encryptedBase64: String): String

    fun createEncryptedSharedPreferences(fileName: String): SharedPreferences
}
