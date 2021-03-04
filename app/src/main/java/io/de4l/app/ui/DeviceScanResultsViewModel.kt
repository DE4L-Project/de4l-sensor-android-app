package io.de4l.app.ui

import androidx.lifecycle.*
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.BluetoothScanState
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DeviceScanResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val deviceRepository: DeviceRepository,
    val bluetoothDeviceManager: BluetoothDeviceManager
) : ViewModel() {

    val foundDevices: MutableLiveData<DeviceEntity> = MutableLiveData()
    var scanState: LiveData<BluetoothScanState> = bluetoothDeviceManager
        .bluetoothScanScanState
        .asLiveData()

    var scanStarted = false

    @ExperimentalCoroutinesApi
    fun startScanning() {
        scanStarted = true
        viewModelScope.launch {
            bluetoothDeviceManager
                .scanForDevices()
                .filter {
                    deviceRepository.isDeviceSupported(it)
                }
                .filterNot {
                    deviceRepository.containsDeviceAddress(it.address)
                }
                .collect {
                    foundDevices.value = DeviceEntity(null, it.name, it.address)
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
}