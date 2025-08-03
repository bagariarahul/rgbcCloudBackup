package com.rgbc.cloudBackup.core.security

/**
 * Exception thrown when cryptographic operations fail
 */
class CryptoException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
