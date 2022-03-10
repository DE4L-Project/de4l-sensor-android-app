package io.de4l.app.tracking

import android.util.Log
import io.de4l.app.AppConstants
import io.de4l.app.BuildConfig
import io.de4l.app.auth.AuthManager
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.event.LocationUpdateEvent
import io.de4l.app.mqtt.LocationMqttMessage
import io.de4l.app.mqtt.MqttManager
import io.de4l.app.mqtt.SensorValueMqttMessage
import io.de4l.app.ui.event.SendSensorValueMqttEvent
import io.de4l.app.ui.event.SensorValueReceivedEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class TrackingManager @Inject constructor(
    val mqttManager: MqttManager,
    val authManager: AuthManager,
    val deviceRepository: DeviceRepository
) {

    private val LOG_TAG: String = TrackingManager::class.java.getName()

    val trackingState = MutableStateFlow(TrackingState.NOT_TRACKING)
    val messageNumber: AtomicLong = AtomicLong(0L)
    var trackingSessionId: String? = null

    init {
        EventBus.getDefault().register(this)
    }

    @ExperimentalCoroutinesApi
    suspend fun startTracking() {
        trackingSessionId = UUID.randomUUID().toString()

        //Location only mode is only possible
        val connectedDevices = deviceRepository.getDevicesShouldBeConnected().firstOrNull()
        val user = authManager.user.value

        trackingState.value =
            when (user != null && user.isTrackOnlyUser() && connectedDevices == null || connectedDevices?.isEmpty() == true) {
                true -> TrackingState.LOCATION_ONLY
                else -> TrackingState.TRACKING
            }

        mqttManager.connectWithRetry()
    }

    suspend fun stopTracking() {
        trackingSessionId = null
        trackingState.value = TrackingState.NOT_TRACKING
        mqttManager.disconnect()
    }

    fun dispose() {
        EventBus.getDefault().unregister(this)
    }

    @ExperimentalCoroutinesApi
    @Subscribe
    fun onSensorValueReceived(event: SendSensorValueMqttEvent) {
        if (trackingState.value == TrackingState.TRACKING) {
            event.sensorValue?.let {
                mqttManager.publishForCurrentUser(
                    SensorValueMqttMessage(
                        event.sensorValue,
                        authManager.user.value?.username ?: "Unknown user.",
                        BuildConfig.VERSION_NAME,
                        AppConstants.MQTT_TOPIC_PATTERN_SENSOR_VALUES,
                        trackingSessionId ?: "null"
                    )
                )
            }
        }
    }

    @ExperimentalCoroutinesApi
    @Subscribe
    fun onLocationUpdateReceived(event: LocationUpdateEvent) {
        if (trackingState.value == TrackingState.LOCATION_ONLY) {
            Log.i(LOG_TAG, "Tracking only mode")
            Log.i(LOG_TAG, "${event.location.longitude} | ${event.location.latitude}")

            mqttManager.publishForCurrentUser(
                LocationMqttMessage(
                    event.location,
                    authManager.user.value?.username ?: "Unknown user.",
                    BuildConfig.VERSION_NAME,
                    AppConstants.MQTT_TOPIC_PATTERN_LOCATION_VALUES,
                    trackingSessionId ?: "null"
                )
            )


        }
    }

}