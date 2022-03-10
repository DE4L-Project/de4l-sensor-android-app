package io.de4l.app.device

import android.bluetooth.BluetoothDevice
import android.util.Log
import io.de4l.app.De4lApplication
import io.de4l.app.bluetooth.BleConnectionManager
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.sensor.AirBeamSensorValueParser
import io.de4l.app.sensor.SensorValue
import io.de4l.app.util.RetryException
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import no.nordicsemi.android.ble.ktx.suspend
import org.joda.time.DateTime
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class BleDevice(
    name: String?,
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
) : DeviceEntity(name, macAddress, targetConnectionState) {
    var connection: BleConnectionManager? = null

    private val LOG_TAG: String = BleDevice::class.java.name

    override suspend fun connect() {
        onConnecting()
        bluetoothDevice?.let {
            runWithRetry {
                suspendCoroutine { cont: Continuation<Void?> ->
                    connection = BleConnectionManager(De4lApplication.context, object :
                        BleConnectionManager.ConnectionListener {
                        override fun onDisconnected() {
                            if (_targetConnectionState.value === BluetoothConnectionState.CONNECTED) {
                                onReconnecting()
                                //Causes Retry
                                cont.resumeWithException(RetryException("Disconnected"))
                            } else {
                                cont.resume(null)
                            }
                        }

                        override fun onDataReceived(data: String, device: BluetoothDevice) {
                            _sensorValues.value =
                                AirBeamSensorValueParser.parseLine(device.address, data, DateTime())
                        }
                    })

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
}