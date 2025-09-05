package com.rgbc.cloudBackup.core.utils

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileLoggingTree(private val context: Context) : Timber.DebugTree() {
    private val logFile = File(context.filesDir, "app_logs.txt")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        super.log(priority, tag, message, t)
        try {
            val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMsg = "$timeStamp [$priority][$tag]: $message\n"
            logFile.appendText(logMsg)
        } catch (e: IOException) {
            // fallback: ignore
        }
    }
}
