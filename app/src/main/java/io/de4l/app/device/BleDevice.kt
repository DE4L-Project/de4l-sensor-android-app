package io.de4l.app.device

import android.util.Log
import io.de4l.app.bluetooth.BluetoothConnectionState
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend

abstract class BleDevice(
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.NONE
) : DeviceEntity(macAddress, targetConnectionState) {
    var connection: BleManager? = null

    private val LOG_TAG: String = BleDevice::class.java.name

    override suspend fun connect() {
//        onConnecting()

        bluetoothDevice?.let {
            connection = getBleConnection()
            connection.let { connection ->
                connection?.connect(it)!!
                    .fail { bluetoothDevice, status ->
                        Log.v(
                            LOG_TAG,
                            "BLE Connection failed - Status ${status} - Device: ${bluetoothDevice.address}"
                        )
                    }
                    .done {
                        onConnected()
                    }
                    .enqueue()
            }
        }
    }

    override suspend fun forceReconnect() {
        TODO("Not yet implemented")
    }

    override suspend fun disconnect() {
        Log.i(LOG_TAG, "BleDeviceTest - BleDevice::disconnect - ${Thread.currentThread().name}")
        try {
            connection?.disconnect()?.suspend()
        } catch (e: Exception) {
            Log.v(LOG_TAG, "Error in BLE disconnect: ${e.message}")
        }
        onDisconnected()
    }

    abstract fun getBleConnection(): BleManager
}