package com.rgbc.cloudBackup

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CloudBackupApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize SQLCipher native library
        initializeSQLCipher()

        // Initialize logging
        initializeLogging()

        Timber.i("CloudBackup Application started")
    }

    /**
     * Initialize SQLCipher native library
     */
    private fun initializeSQLCipher() {
        try {
            System.loadLibrary("sqlcipher")
            Timber.d("SQLCipher native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load SQLCipher native library")
            throw RuntimeException("SQLCipher initialization failed", e)
        }
    }

    private fun initializeLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
