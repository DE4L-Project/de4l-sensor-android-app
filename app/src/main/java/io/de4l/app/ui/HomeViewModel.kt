package io.de4l.app.ui

import android.app.Application
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.BuildConfig
import io.de4l.app.R
import io.de4l.app.auth.AuthManager
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.Location
import io.de4l.app.location.LocationService
import io.de4l.app.location.event.LocationUpdateEvent
import io.de4l.app.sensor.SensorType
import io.de4l.app.tracking.BackgroundServiceWatcher
import io.de4l.app.tracking.TrackingManager
import io.de4l.app.tracking.TrackingState
import io.de4l.app.ui.event.NavigationEvent
import io.de4l.app.ui.event.SensorValueReceivedEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val locationManager: LocationService,
    private val backgroundServiceWatcher: BackgroundServiceWatcher,
    private val deviceRepository: DeviceRepository,
    private val bluetoothDeviceManager: BluetoothDeviceManager,
    private val trackingManager: TrackingManager,
    private val application: Application
) : ViewModel() {

    val temperature = MutableLiveData<Double?>()
    val humidity = MutableLiveData<Double?>()
    val pm1 = MutableLiveData<Double?>()
    val pm25 = MutableLiveData<Double?>()
    val pm10 = MutableLiveData<Double?>()

    val location = MutableLiveData<Location?>(locationManager.getCurrentLocation())
    val user = authManager.user.asLiveData()
    val versionInfo =
        if (BuildConfig.DEBUG) BuildConfig.VERSION_NAME + "-dev" else BuildConfig.VERSION_NAME

    lateinit var trackingEnabled: LiveData<Boolean>
    lateinit var connectedDevices: LiveData<List<DeviceEntity>>
    lateinit var bluetoothConnectionState: LiveData<BluetoothConnectionState>
    lateinit var trackingState: LiveData<TrackingState>

    init {
        EventBus.getDefault().register(this)

        viewModelScope.launch {
            connectedDevices = deviceRepository.connectedDevices.asLiveData()
            bluetoothConnectionState = bluetoothDeviceManager.bluetoothConnectionState.asLiveData()

            launch {
                bluetoothDeviceManager.bluetoothConnectionState.collect {
                    if (it != BluetoothConnectionState.CONNECTED) {
                        clearData()
                    }
                }
            }

            trackingState = trackingManager.trackingState.asLiveData()

            trackingEnabled =
                deviceRepository.getConnectedDevices()
                    .combine(authManager.user) { connectedDevices, user ->
                        connectedDevices.isNotEmpty() && user != null
                    }
                    .asLiveData()
        }
    }

    private fun clearData() {
        temperature.value = null
        humidity.value = null
        pm1.value = null
        pm25.value = null
        pm10.value = null
    }

    @ExperimentalCoroutinesApi
    fun onToggleTrackingClicked() {
        viewModelScope.launch {
            if (trackingState.value == TrackingState.TRACKING) {
                trackingManager.stopTracking()
            } else {
                trackingManager.startTracking()
            }
        }
    }

    fun onBtConnectClicked() {
        if (bluetoothConnectionState.value != BluetoothConnectionState.DISCONNECTED) {
            viewModelScope.launch {
                bluetoothDeviceManager.disconnectAllDevices()
            }
        } else {
            // Go to connect Page
            EventBus.getDefault().post(NavigationEvent(R.id.devices))
        }
    }

    override fun onCleared() {
        EventBus.getDefault().unregister(this)
        super.onCleared()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLocationUpdate(locationUpdateEvent: LocationUpdateEvent) {
        location.value = locationUpdateEvent.location
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSensorValueReceived(event: SensorValueReceivedEvent) {
        val sensorValue = event.sensorValue
        when (sensorValue.sensorType) {
            SensorType.TEMPERATURE -> temperature.value = sensorValue.value
            SensorType.HUMIDITY -> humidity.value = sensorValue.value
            SensorType.PM1 -> pm1.value = sensorValue.value
            SensorType.PM2_5 -> pm25.value = sensorValue.value
            SensorType.PM10 -> pm10.value = sensorValue.value
        }
    }

    fun onUserButtonClicked(activity: FragmentActivity) {
        if (user.value != null) {
            viewModelScope.launch {
                authManager.logout(activity)
            }
        } else {
            viewModelScope.launch {
                try {
                    authManager.login(activity)
                } catch (e: Exception) {
                    authManager.logout(activity)
                    Toast.makeText(
                        application,
                        "Login error: " + (e.message ?: "Unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    }
}