package io.de4l.app.tracking

import android.app.Application
import android.content.Intent
import io.de4l.app.bluetooth.BluetoothDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

class BackgroundServiceWatcher(val application: Application) {
    private val LOG_TAG: String = BluetoothDeviceManager::class.java.name
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    val isBackgroundServiceActive = MutableStateFlow(BackgroundServiceState.NOT_RUNNING)

    fun startBackgroundService() {
        val startIntent = Intent(application, BackgroundService::class.java)
        application.startService(startIntent)
    }


    //Starts background service when not running
    fun sendEventToService(event: Any) {
        if (isBackgroundServiceActive.value === BackgroundServiceState.NOT_RUNNING) {
            isBackgroundServiceActive.value = BackgroundServiceState.PENDING
            startBackgroundService()
        }

        coroutineScope.launch {
            isBackgroundServiceActive.filter { it == BackgroundServiceState.RUNNING }.first()
            EventBus.getDefault().post(event)
        }
    }

    fun dispose() {
        EventBus.getDefault().unregister(this)
        coroutineScope.cancel()
    }

}