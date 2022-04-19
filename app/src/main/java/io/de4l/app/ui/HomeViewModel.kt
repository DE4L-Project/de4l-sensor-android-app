package io.de4l.app.ui

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.BuildConfig
import io.de4l.app.R
import io.de4l.app.auth.AuthManager
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.event.ConnectToBluetoothDeviceEvent
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.LocationService
import io.de4l.app.location.LocationValue
import io.de4l.app.location.event.LocationUpdateEvent
import io.de4l.app.sensor.SensorValue
import io.de4l.app.tracking.BackgroundServiceWatcher
import io.de4l.app.tracking.TrackingManager
import io.de4l.app.tracking.TrackingState
import io.de4l.app.ui.event.NavigationEvent
import io.de4l.app.ui.event.StartLocationServiceEvent
import io.de4l.app.ui.event.StopLocationServiceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val LOG_TAG = HomeViewModel::class.java.name

    private var job: Job? = null

    val location = MutableLiveData<LocationValue?>(locationManager.getCurrentLocation())
    val user = authManager.user.asLiveData()
    val versionInfo =
        if (BuildConfig.DEBUG) BuildConfig.VERSION_NAME + "-dev" else BuildConfig.VERSION_NAME

    lateinit var trackingEnabled: LiveData<Boolean>
    lateinit var linkedDevices: LiveData<List<DeviceEntity>>
    lateinit var connectedDevices: LiveData<List<DeviceEntity>>
    lateinit var trackingState: LiveData<TrackingState>

    var sensorValues: MutableStateFlow<SensorValue?> = MutableStateFlow(null)
    val selectedDevice: MutableStateFlow<DeviceEntity?> = MutableStateFlow(null)

    init {
        EventBus.getDefault().register(this)

        viewModelScope.launch {
            linkedDevices = deviceRepository.getDevices().asLiveData()
            connectedDevices = deviceRepository.getDevicesShouldBeConnected().asLiveData()
            trackingState = trackingManager.trackingState.asLiveData()

            trackingEnabled =
                deviceRepository.getConnectedDevices()
                    .combine(authManager.user) { connectedDevices, user ->
                        Log.v(LOG_TAG, "connectedDevices: ${connectedDevices.size}")
                        Log.v(LOG_TAG, "user: ${user}")
                        connectedDevices.isNotEmpty() && user != null || user?.isTrackOnlyUser() == true
                    }
                    .asLiveData()
        }
    }

    private fun clearData() {
    }

    @ExperimentalCoroutinesApi
    fun onToggleTrackingClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            if (trackingState.value == TrackingState.TRACKING || trackingState.value == TrackingState.LOCATION_ONLY) {
                trackingManager.stopTracking()
            } else {
                trackingManager.startTracking()
            }
        }
    }

    fun onBtConnectClicked() {
        if (connectedDevices.value?.isNotEmpty() == true) {
            viewModelScope.launch(Dispatchers.IO) {
                bluetoothDeviceManager.disconnectAllDevices()
            }
        } else {
            if (linkedDevices.value?.isEmpty() == true) {
                EventBus.getDefault().post(NavigationEvent(R.id.devices))
            } else {
                linkedDevices.value?.forEach {
                    viewModelScope.launch(Dispatchers.IO) {
                        it._macAddress.value?.let { macAddress ->
                            backgroundServiceWatcher.sendEventToService(
                                ConnectToBluetoothDeviceEvent(macAddress)
                            )
                        }
                    }
                }
            }
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

    fun startLocationUpdates() {
        backgroundServiceWatcher.sendEventToService(StartLocationServiceEvent())
    }

    fun stopLocationUpdates() {
        backgroundServiceWatcher.sendEventToService(StopLocationServiceEvent())
    }

    fun disconnectDevice(device: DeviceEntity) {
        bluetoothDeviceManager.disconnect(device)
    }

    fun onDeviceButtonClicked(device: DeviceEntity) {
        selectedDevice.value = device
    }
}