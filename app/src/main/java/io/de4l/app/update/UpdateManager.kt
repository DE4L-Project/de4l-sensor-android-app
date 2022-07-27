package io.de4l.app.update

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import io.de4l.app.AppConstants
import kotlinx.coroutines.*
import org.joda.time.DateTime
import kotlin.coroutines.resume

class UpdateManager(
    private val context: Context,
    private val appUpdateManager: AppUpdateManager
) {
    private val LOG_TAG = UpdateManager::class.java.name
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val INTENT_REQUEST_CODE = 5000

    fun startUpdateFlow(checkUpdateResponse: CheckUpdateResponse, activity: Activity) {
        val updateInfo = checkUpdateResponse.appUpdateInfo
        updateInfo?.let {
            saveLastUpdateTimestamp(DateTime.now())
            appUpdateManager.startUpdateFlowForResult(
                checkUpdateResponse.appUpdateInfo,
                AppUpdateType.IMMEDIATE,
                activity,
                AppConstants.UPDATE_FLOW_REQUEST_CODE
            )
        }
    }

    suspend fun checkForUpdates(): CheckUpdateResponse {

        //Buffer update checks, called on every onResume for HomeView
        val lastUpdateTimestamp = this.getLastUpdateTimestamp()
        Log.v(LOG_TAG, "Last update @ $lastUpdateTimestamp")

        val isMinimumIntervalExceeded = lastUpdateTimestamp == null || lastUpdateTimestamp
            .plusHours(AppConstants.UPDATE_CHECK_MINIMUM_INTERVAL_HOURS)
            .isBeforeNow
        Log.v(LOG_TAG, "isMinimumIntervalExceeded: $isMinimumIntervalExceeded")

        if (!isMinimumIntervalExceeded) {
            return CheckUpdateResponse.emptyResponse()
        }

        return suspendCancellableCoroutine<CheckUpdateResponse> { continuation ->

            //Fallback if no response is received
            val timeoutJob = coroutineScope.launch {
                delay(10000L)
                if (continuation.isActive) {
                    continuation.resume(CheckUpdateResponse.emptyResponse())
                }
            }

            //Call Play Store API
            appUpdateManager
                .appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->
                    val updateAvailability: Int = appUpdateInfo.updateAvailability()

                    //Happens when update process crashed
                    val isUpdatePending =
                        updateAvailability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    Log.v(LOG_TAG, "Is update pending: $isUpdatePending")

                    val isUpdateAvailable =
                        updateAvailability == UpdateAvailability.UPDATE_AVAILABLE
                    Log.v(
                        LOG_TAG,
                        "appUpdateInfo.updateAvailability(): $updateAvailability"
                    )
                    Log.v(LOG_TAG, "Is update available: $isUpdateAvailable")

                    val isUpdateAllowed = appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    Log.v(LOG_TAG, "Is update allowed: $isUpdateAllowed")

                    if (timeoutJob.isActive) {
                        timeoutJob.cancel()
                    }
                    if (continuation.isActive) {
                        continuation.resume(
                            CheckUpdateResponse(
                                isUpdatePending || (isUpdateAvailable && isUpdateAllowed),
                                appUpdateInfo
                            )
                        )
                    }
                }
                .addOnFailureListener {
                    Log.e(LOG_TAG, "Error in update check: $it")
                    if (timeoutJob.isActive) {
                        timeoutJob.cancel()
                    }
                    if (continuation.isActive) {
                        continuation.resume(CheckUpdateResponse.emptyResponse())
                    }
                }
        }
    }

    private fun getLastUpdateTimestamp(): DateTime? {
        val lastUpdateTimestampIso =
            getSharedPreferences().getString(LAST_UPDATE_TIMESTAMP_ISO_KEY, null)
        return if (lastUpdateTimestampIso == null) null else DateTime(lastUpdateTimestampIso)
    }

    private fun saveLastUpdateTimestamp(lastUpdateTimestamp: DateTime) {
        val preferenceEditor: SharedPreferences.Editor = getSharedPreferences().edit()
        preferenceEditor.putString(LAST_UPDATE_TIMESTAMP_ISO_KEY, lastUpdateTimestamp.toString())
        preferenceEditor.apply()
    }

    private fun getSharedPreferences(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    companion object {
        private val LAST_UPDATE_TIMESTAMP_ISO_KEY =
            "io.de4l.app.update.UpdateCheckManager::lastUpdateTimestampIso"
    }

    class CheckUpdateResponse(
        val isUpdateAvailable: Boolean,
        val appUpdateInfo: AppUpdateInfo?
    ) {

        companion object {
            fun emptyResponse(): CheckUpdateResponse {
                return CheckUpdateResponse(false, null)
            }
        }
    }

}

