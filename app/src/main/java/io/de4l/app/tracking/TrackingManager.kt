package io.de4l.app.tracking

import android.app.Application
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import androidx.preference.PreferenceManager
import com.hoc081098.flowext.interval
import io.de4l.app.AppConstants
import io.de4l.app.BuildConfig
import io.de4l.app.auth.AuthManager
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.event.LocationUpdateEvent
import io.de4l.app.mqtt.HeartbeatMqttMessage
import io.de4l.app.mqtt.LocationMqttMessage
import io.de4l.app.mqtt.MqttManager
import io.de4l.app.mqtt.SensorValueMqttMessage
import io.de4l.app.ui.event.SendSensorValueMqttEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.joda.time.DateTime
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class TrackingManager @Inject constructor(
    val mqttManager: MqttManager,
    val authManager: AuthManager,
    val deviceRepository: DeviceRepository,
    val powerManager: PowerManager,
    val batteryManager: BatteryManager,
    val application: Application
) {

    private val LOG_TAG: String = TrackingManager::class.java.getName()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    val trackingState = MutableStateFlow(TrackingState.NOT_TRACKING)
    val messageNumber: AtomicLong = AtomicLong(0L)
    var trackingSessionId: String? = null

    var heartbeatJob: Job? = null;

    private val applicationId: String

    init {
        EventBus.getDefault().register(this)
        var storedApplicationId = loadApplicationId()
        if (storedApplicationId == null) {
            storedApplicationId = UUID.randomUUID().toString()
            saveApplicationId(storedApplicationId)
        }
        this.applicationId = storedApplicationId
    }

    private fun loadApplicationId(): String? {
        return PreferenceManager.getDefaultSharedPreferences(application)
            .getString(APPLICATION_ID_KEY, null)

    }

    private fun saveApplicationId(applicationId: String) {
        val preferenceEditor: SharedPreferences.Editor =
            PreferenceManager.getDefaultSharedPreferences(application).edit()
        preferenceEditor.putString(
            APPLICATION_ID_KEY, applicationId
        )
        preferenceEditor.apply()
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

        heartbeatJob?.cancel()
        heartbeatJob = coroutineScope.launch {
            interval(
                AppConstants.HEARTBEAT_INTERVAL_SECONDS * 1000,
                AppConstants.HEARTBEAT_INTERVAL_SECONDS * 1000
            ).collect {
                sendHeartbeat()
            }

        }

    }

    suspend fun stopTracking() {
        trackingSessionId = null
        trackingState.value = TrackingState.NOT_TRACKING
        mqttManager.disconnect()
        heartbeatJob?.cancel()
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
                        applicationId,
                        authManager.user.value?.username ?: "Unknown user.",
                        BuildConfig.VERSION_NAME,
                        AppConstants.MQTT_TOPIC_PATTERN_SENSOR_VALUES,
                        trackingSessionId ?: "null",
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

    @ExperimentalCoroutinesApi
    fun sendHeartbeat() {
        val batteryPercentage =
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val heartbeatValue =
            HeartbeatValue(
                DateTime.now(),
                applicationId,
                batteryPercentage,
                powerManager.isPowerSaveMode
            )

        mqttManager.publishForCurrentUser(
            HeartbeatMqttMessage(
                heartbeatValue,
                authManager.user.value?.username ?: "Unknown user.",
                BuildConfig.VERSION_NAME,
                AppConstants.HEARTBEAT_TOPIC_PATTERN_LOCATION_VALUES,
                trackingSessionId ?: "null"
            )
        )
    }

    companion object {
        private val APPLICATION_ID_KEY =
            "io.de4l.app.update.TrackingManager::applicationId"
    }
}