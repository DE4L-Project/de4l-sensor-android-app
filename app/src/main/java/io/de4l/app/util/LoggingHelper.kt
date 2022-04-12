package io.de4l.app.util

import android.util.Log

class LoggingHelper {

    companion object {
        fun logCurrentThread(tag: String, message: String) {
            Log.v(tag, Thread.currentThread().name + " | " + message)
        }
    }
}