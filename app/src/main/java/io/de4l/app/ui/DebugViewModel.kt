package io.de4l.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.event.BluetoothDeviceConnectedEvent
import io.de4l.app.bluetooth.event.ConnectToBluetoothDeviceEvent
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.tracking.BackgroundServiceWatcher
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

    private val LOG_TAG: String = DebugViewModel::class.java.name


    lateinit var connectionState: LiveData<BluetoothConnectionState>
    lateinit var _airbeam3: LiveData<DeviceEntity?>
    lateinit var _airbeam2: LiveData<DeviceEntity?>


    private val AIRBEAM3_TEST_ADDRESS = "E8:68:E7:38:89:CA"

    //    private val AIRBEAM3_TEST_ADDRESS = "E8:68:E7:38:88:A2"
    private val AIRBEAM2_TEST_ADDRESS = "00:11:E4:00:05:28"

    init {
        viewModelScope.launch {
//            connectionState = bluetoothDeviceManager.bluetoothConnectionState.asLiveData()
            _airbeam3 = bluetoothDeviceManager.deviceRepository
                .getByAddress(AIRBEAM3_TEST_ADDRESS)
                .asLiveData()

            _airbeam2 = bluetoothDeviceManager.deviceRepository
                .getByAddress(AIRBEAM2_TEST_ADDRESS)
                .asLiveData()
        }

        EventBus.getDefault().register(this)
    }

    override fun onCleared() {
        EventBus.getDefault().unregister(this)
        super.onCleared()
    }

    fun onConnectToAirBeam3() {
        Log.i(
            LOG_TAG,
            "BleDeviceTest - DebugViewModel::onConnectToAirBeam3 - ${Thread.currentThread().name}"
        )
        val airBeam3TestMacAddress = AIRBEAM3_TEST_ADDRESS
        if (_airbeam3.value?._actualConnectionState?.value === BluetoothConnectionState.DISCONNECTED) {
            viewModelScope.launch {
                backgroundServiceWatcher.sendEventToService(
                    ConnectToBluetoothDeviceEvent(
                        airBeam3TestMacAddress
                    )
                )
            }
        } else {
            viewModelScope.launch {
                Log.i(
                    LOG_TAG,
                    "BleDeviceTest - DebugViewModel::onConnectToAirBeam3/disconnect - ${Thread.currentThread().name}"
                )
                bluetoothDeviceManager.disconnect(_airbeam3.value!!)
            }
        }
    }

    fun onConnectToAirBeam2() {
        val airBeam2TestMacAddress = AIRBEAM2_TEST_ADDRESS

        viewModelScope.launch {
            if (_airbeam2.value?._actualConnectionState?.value === BluetoothConnectionState.DISCONNECTED) {
                backgroundServiceWatcher.sendEventToService(
                    ConnectToBluetoothDeviceEvent(
                        airBeam2TestMacAddress
                    )
                )
            } else {
                bluetoothDeviceManager.disconnect(_airbeam2.value!!)
            }
        }

    }

    @Subscribe
    fun onBluetoothDeviceConnected(event: BluetoothDeviceConnectedEvent) {
    }

    fun onConnectionLoss() {
        viewModelScope.launch {
            _airbeam2.value?.disconnect()
        }
    }


}