package com.rgbc.cloudBackup.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android Keystore–backed implementation of [KeyProvider].
 */
class AndroidKeystoreKeyProvider(
    private val alias: String = "RGBC_BACKUP_AES_KEY"
) : KeyProvider {

    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    override fun getOrCreateAesKey(): SecretKey {
        // If the key already exists, return it
        keyStore.getKey(alias, null)?.let { return it as SecretKey }

        // Otherwise, generate a new AES/GCM key
        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Require user auth for each use (optional–remove if you want no biometric gating)
            // setUserAuthenticationRequired(true)
        }.build()

        keyGen.init(spec)
        return keyGen.generateKey()
    }
}
