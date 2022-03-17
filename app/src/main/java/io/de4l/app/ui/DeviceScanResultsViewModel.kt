package io.de4l.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.bluetooth.BluetoothScanState
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceScanResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val deviceRepository: DeviceRepository,
    val bluetoothDeviceManager: BluetoothDeviceManager
) : ViewModel() {

    private val LOG_TAG: String = DeviceScanResultsViewModel::class.java.name

    val foundDevices: MutableLiveData<DeviceEntity> = MutableLiveData()
    var scanState: LiveData<BluetoothScanState> = bluetoothDeviceManager
        .bluetoothScanScanState
        .asLiveData()

    var scanStarted = false

    @SuppressLint("MissingPermission")
    @ExperimentalCoroutinesApi
    fun startScanning() {
        scanStarted = true
        viewModelScope.launch {
            bluetoothDeviceManager
                .scanForDevices()
                .map {
                    Log.i(
                        LOG_TAG,
                        it.address + ": [" + it.name + "] " + getDeviceTypeAsString(it.type)
                    )
                    it
                }
                .filter {
                    deviceRepository.isDeviceSupported(it)
                }
                .filterNot {
                    deviceRepository.containsDeviceAddress(it.address)
                }
                .collect {
                    foundDevices.value =
                        DeviceEntity.fromBluetoothDevice(it)
                }
        }
    }

    fun stopScanning() {
        bluetoothDeviceManager.cancelDeviceDiscovery()
    }


    fun onDeviceSelected(device: DeviceEntity) {
        viewModelScope.launch {
            deviceRepository.addDevice(device)
        }
    }

    fun getDeviceTypeAsString(deviceType: Int): String {
        return when (deviceType) {
            1 -> "DEVICE_TYPE_CLASSIC"
            2 -> "DEVICE_TYPE_LE"
            3 -> "DEVICE_TYPE_DUAL"
            else -> "DEVICE_TYPE_UNKNOWN"
        }
    }

    fun getBluetoothDeviceType(bluetoothDevice: BluetoothDevice): BluetoothDeviceType {
        return BluetoothDeviceManager.getDeviceTypeForBluetoothDevice(bluetoothDevice)
    }
}