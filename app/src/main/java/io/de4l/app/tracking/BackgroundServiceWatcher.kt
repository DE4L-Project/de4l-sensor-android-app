package io.de4l.app.tracking

import android.app.Application
import android.content.Intent
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.ui.event.StartTrackingServiceEvent
import io.de4l.app.ui.event.StopTrackingServiceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe

class BackgroundServiceWatcher(val application: Application) {
    private val LOG_TAG: String = BluetoothDeviceManager::class.java.name

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        EventBus.getDefault().register(this)
    }

    val isBackgroundServiceActive = MutableStateFlow(false)

    fun startTrackingService() {
        val startIntent = Intent(application, BackgroundService::class.java)
        application.startService(startIntent)
    }

    @Subscribe
    fun onStartTrackingServiceEvent(event: StartTrackingServiceEvent) {
        isBackgroundServiceActive.value = true
    }

    @Subscribe
    fun onStopTrackingServiceEvent(event: StopTrackingServiceEvent) {
        isBackgroundServiceActive.value = false
    }

    //Starts background service when not running
    fun sendEventToService(event: Any) {
        if (!isBackgroundServiceActive.value) {
            startTrackingService()
        }

        coroutineScope.launch {
            isBackgroundServiceActive.filter { it }.first()
            EventBus.getDefault().post(event)
        }
    }

    fun dispose() {
        EventBus.getDefault().unregister(this)
        coroutineScope.cancel()
    }

}