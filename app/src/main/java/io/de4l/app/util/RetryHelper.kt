package io.de4l.app.util

import android.util.Log
import kotlinx.coroutines.delay

class RetryHelper {
    companion object {
        private val LOG_TAG: String = RetryHelper::class.java.name

        //https://stackoverflow.com/questions/46872242/how-to-exponential-backoff-retry-on-kotlin-coroutines
        suspend fun <T> runWithRetry(
            times: Int = Int.MAX_VALUE,
            initialDelay: Long = 1000, // 1 second
            maxDelay: Long = 60000,    // 1 minute
            factor: Double = 2.0,
            block: suspend () -> T
        ): T {
            var currentDelay = initialDelay
            repeat(times - 1) {
                try {
                    return block()
                } catch (e: RetryException) {
                    Log.v(LOG_TAG, "Caught retry exception: ${e.message ?: "Unknown message"}")
                }
                Log.v(LOG_TAG, "Retry in $currentDelay ms...")
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
            Log.v(LOG_TAG, "Last retry attempt")
            return block()
        }
    }
}