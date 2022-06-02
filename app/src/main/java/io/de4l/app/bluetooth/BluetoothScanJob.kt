package io.de4l.app.bluetooth

import android.bluetooth.BluetoothDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

data class BluetoothScanJob(
    val macAddress: String,
    val deviceType: BluetoothDeviceType,
    val retry: Boolean = false
) {

    private val _device: MutableSharedFlow<BluetoothDevice?> = MutableSharedFlow()

    suspend fun waitForDevice(): BluetoothDevice? {
        return _device.first()
    }

    suspend fun onSuccess(device: BluetoothDevice?) {
        _device.emit(device)
    }

    suspend fun onError() {
        _device.emit(null)
    }

    fun isLegacyScanJob(): Boolean {
        return this.deviceType === BluetoothDeviceType.AIRBEAM2
    }
}