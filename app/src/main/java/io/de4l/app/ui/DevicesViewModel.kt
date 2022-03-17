package io.de4l.app.ui

import android.app.AlertDialog
import android.app.Application
import android.view.View
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.R
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.event.BluetoothDeviceConnectedEvent
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.tracking.BackgroundServiceWatcher
import io.de4l.app.ui.event.NavigationEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject

@HiltViewModel
class DevicesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bluetoothDeviceManager: BluetoothDeviceManager,
    private val backgroundServiceWatcher: BackgroundServiceWatcher,
    private val deviceRepository: DeviceRepository,
    application: Application
) : AndroidViewModel(application) {

    lateinit var _devices: LiveData<List<DeviceEntity>>

    init {
        viewModelScope.launch {
            _devices = deviceRepository.getDevices().asLiveData()
        }

        EventBus.getDefault().register(this)
    }

    override fun onCleared() {
        EventBus.getDefault().unregister(this)
        super.onCleared()
    }

    fun onLongPress(view: View, device: DeviceEntity) {
        device.let {
            val builder = AlertDialog.Builder(view.context)
            builder.setMessage("Delete paired device [${it._macAddress.value}] from DE4L Sensor app?")
                .setPositiveButton("Yes") { dialog, id ->
                    onDeviceRemoveClicked(it)
                }
                .setNegativeButton("No") { dialog, id ->
                    //Do Nothing
                }

            val dialog = builder.create()
            dialog.show()
        }
    }

    fun onDeviceConnectClicked(device: DeviceEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                device.let { device ->
                    if (device._actualConnectionState.value !== BluetoothConnectionState.CONNECTED) {
                        device._targetConnectionState.value = BluetoothConnectionState.CONNECTED
                        device._actualConnectionState.value = BluetoothConnectionState.CONNECTING
                        device._macAddress.value?.let {
                            //Must find device first
                            launch { bluetoothDeviceManager.connectDeviceWithRetry(it) }
                        }

                    } else {
                        device._targetConnectionState.value = BluetoothConnectionState.DISCONNECTED
                        deviceRepository.updateDevice(device)
                        bluetoothDeviceManager.disconnect(device)
                    }
                }
            }
        }
    }

    private fun onDeviceRemoveClicked(device: DeviceEntity) {
        viewModelScope.launch {
            if (device._actualConnectionState.value != BluetoothConnectionState.DISCONNECTED) {
                bluetoothDeviceManager.disconnect(device)
            }

            device._macAddress.value?.let {
                deviceRepository.removeByAddress(it)
            }
        }
    }


    @Subscribe
    fun onBluetoothDeviceConnected(event: BluetoothDeviceConnectedEvent) {
        EventBus.getDefault().post(NavigationEvent(R.id.action_devices_to_homeFragment))
    }

}