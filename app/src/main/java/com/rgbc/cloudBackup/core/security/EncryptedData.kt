package com.rgbc.cloudBackup.core.security

import android.util.Base64
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing encrypted data with initialization vector
 */
@Parcelize
data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray
) : Parcelable {

    /**
     * Converts encrypted data to Base64 string for storage/transmission
     */
    fun toBase64String(): String {
        val combined = iv + ciphertext
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }

    companion object {
        private const val GCM_IV_LENGTH = 12

        /**
         * Creates EncryptedData from Base64 string
         */
        fun fromBase64String(base64String: String): EncryptedData {
            val combined = Base64.decode(base64String, Base64.DEFAULT)
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val ciphertext = combined.sliceArray(GCM_IV_LENGTH until combined.size)
            return EncryptedData(ciphertext, iv)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
