package io.de4l.app.device

import android.util.Log
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ktx.suspend
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

abstract class BleDevice(
    name: String?,
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
) : DeviceEntity(name, macAddress, targetConnectionState) {
    var connection: BleManager? = null

    private val LOG_TAG: String = BleDevice::class.java.name

    override suspend fun connect() {
        onConnecting()
        bluetoothDevice?.let {
            runWithRetry {
                suspendCoroutine { cont: Continuation<Void?> ->
                    connection = getBleConnection(cont)
                    connection.let { connection ->
                        connection?.connect(it)!!
                            .done {
                                onConnected()
                            }
                            .enqueue()
                    }
                }
            }
        }
    }

    override suspend fun forceReconnect() {
        TODO("Not yet implemented")
    }

    override suspend fun disconnect() {
        Log.i(LOG_TAG, "BleDeviceTest - BleDevice::disconnect - ${Thread.currentThread().name}")
        connection?.disconnect()?.suspend()
        onDisconnected()
    }

    abstract fun getBleConnection(cont: Continuation<Void?>): BleManager
}