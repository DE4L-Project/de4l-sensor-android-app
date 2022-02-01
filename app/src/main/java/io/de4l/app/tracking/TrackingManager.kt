package io.de4l.app.tracking

import io.de4l.app.mqtt.MqttManager
import io.de4l.app.ui.event.SensorValueReceivedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class TrackingManager @Inject constructor(val mqttManager: MqttManager) {

    val trackingState = MutableStateFlow(TrackingState.NOT_TRACKING)
    val messageNumber: AtomicLong = AtomicLong(0L)

    init {
        EventBus.getDefault().register(this)
    }

    @ExperimentalCoroutinesApi
    suspend fun startTracking() {
        trackingState.value = TrackingState.TRACKING
        mqttManager.connectWithRetry()
    }

    suspend fun stopTracking() {
        trackingState.value = TrackingState.NOT_TRACKING
        mqttManager.disconnect()
    }

    fun dispose() {
        EventBus.getDefault().unregister(this)
    }

    @ExperimentalCoroutinesApi
    @Subscribe
    fun onSensorValueReceived(event: SensorValueReceivedEvent) {
        if (trackingState.value == TrackingState.TRACKING) {
            mqttManager.publishForCurrentUser(event.sensorValue)
        }
    }

}