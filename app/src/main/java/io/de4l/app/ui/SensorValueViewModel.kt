package io.de4l.app.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.device.DeviceEntity
import io.de4l.app.sensor.SensorType
import io.de4l.app.sensor.SensorValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorValueViewModel @Inject constructor(
    private val bluetoothDeviceManager: BluetoothDeviceManager
) : ViewModel() {
    private val LOG_TAG = SensorValueViewModel::class.java.name

    val selectedDevice: MutableStateFlow<DeviceEntity?> = MutableStateFlow(null)
//    val sensorValues: MutableStateFlow<SensorValue?> = MutableStateFlow(null)

    val sensorValueFlows: MutableMap<SensorType, MutableStateFlow<SensorValue?>> = HashMap()

    fun registerUiUpdates() {
        viewModelScope.launch {
            selectedDevice.filterNotNull().collect { selectedDevice ->
                val cachedValuesFlow =
                    selectedDevice.sensorValueCache.entries.asFlow()
                        .map { entry ->
                            Log.v(LOG_TAG, "Cached Entry")
                            entry.value
                        }

                val liveUpdatesFlow = selectedDevice._sensorValues.filterNotNull()

                merge(cachedValuesFlow, liveUpdatesFlow)
                    .collect {
                        getFlowForProperty(it.sensorType).value = it
                    }
            }
        }
    }

    fun getFlowForProperty(sensorType: SensorType): MutableStateFlow<SensorValue?> {
        val flow = sensorValueFlows[sensorType]
        if (flow == null) {
            sensorValueFlows[sensorType] = MutableStateFlow(null)
        }
        return sensorValueFlows[sensorType]!!
    }

    fun onDisconnectClicked() {
        viewModelScope.launch {
            val connectionState = selectedDevice.value?._targetConnectionState?.value
            if (connectionState !== BluetoothConnectionState.DISCONNECTED) {
                selectedDevice.value?._targetConnectionState?.value =
                    BluetoothConnectionState.DISCONNECTED
                selectedDevice.value?.disconnect()
            } else {
                selectedDevice.value?._targetConnectionState?.value =
                    BluetoothConnectionState.CONNECTED
                selectedDevice.value?._macAddress?.value?.let {
                    launch(Dispatchers.IO) { bluetoothDeviceManager.connectDeviceWithRetry(it) }
                }
            }
        }
    }
}