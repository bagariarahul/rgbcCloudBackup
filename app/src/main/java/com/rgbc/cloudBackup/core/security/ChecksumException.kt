package com.rgbc.cloudBackup.core.security

/**
 * Exception thrown when checksum calculation fails.
 */
class ChecksumException(message: String, cause: Throwable? = null) : Exception(message, cause)
