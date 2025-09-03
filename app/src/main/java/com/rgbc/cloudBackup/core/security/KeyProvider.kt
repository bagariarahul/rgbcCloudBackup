package com.rgbc.cloudBackup.core.security

import javax.crypto.SecretKey

/**
 * Provides a symmetric AES key stored in Android Keystore.
 */
interface KeyProvider {
    /**
     * Returns (or generates, on first call) the AES key under a fixed alias.
     */
    fun getOrCreateAesKey(): SecretKey
}
