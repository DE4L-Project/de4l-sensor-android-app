package io.de4l.app.ui

import android.app.Application
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.R
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.event.BluetoothDeviceConnectedEvent
import io.de4l.app.bluetooth.event.ConnectToBluetoothDeviceEvent
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.tracking.BackgroundServiceWatcher
import io.de4l.app.ui.event.NavigationEvent
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val bluetoothDeviceManager: BluetoothDeviceManager,
    private val backgroundServiceWatcher: BackgroundServiceWatcher,
    private val deviceRepository: DeviceRepository,
    application: Application
) : AndroidViewModel(application) {

    lateinit var connectionState: LiveData<BluetoothConnectionState>
    lateinit var _device: LiveData<DeviceEntity?>


    private val AIRBEAM3_TEST_ADDRESS = "E8:68:E7:38:89:CA"

    init {
        viewModelScope.launch {
//            connectionState = bluetoothDeviceManager.bluetoothConnectionState.asLiveData()
            _device = bluetoothDeviceManager.deviceRepository
                .getByAddress(AIRBEAM3_TEST_ADDRESS)
                .asLiveData()
        }

        EventBus.getDefault().register(this)
    }

    override fun onCleared() {
        EventBus.getDefault().unregister(this)
        super.onCleared()
    }

    fun onDeviceConnectClicked() {
        val airBeam3TestMacAddress = AIRBEAM3_TEST_ADDRESS

        viewModelScope.launch {
            backgroundServiceWatcher.sendEventToService(
                ConnectToBluetoothDeviceEvent(
                    airBeam3TestMacAddress
                )
            )
        }
    }

    @Subscribe
    fun onBluetoothDeviceConnected(event: BluetoothDeviceConnectedEvent) {
    }


}