package io.de4l.app.device

import android.bluetooth.BluetoothDevice
import android.util.Log
import io.de4l.app.bluetooth.AirBeam3BleConnection
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.bluetooth.BluetoothSocketConnection
import io.de4l.app.sensor.AirBeamSensorValueParser
import io.de4l.app.util.RetryException
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.joda.time.DateTime

class AirBeam2Device(
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.NONE
) : DeviceEntity(macAddress, targetConnectionState) {

    private val LOG_TAG: String = AirBeam2Device::class.java.name

    var socketConnection: BluetoothSocketConnection? = null

    private var bluetoothConnectionJob: Job? = null

    override suspend fun forceReconnect() {
        socketConnection?.simulateConnectionLoss()
    }

    override suspend fun connect() {
        closeConnection()
        bluetoothConnectionJob = coroutineScope.launch(Dispatchers.IO) {
            bluetoothDevice?.let {
                try {
                    socketConnection = BluetoothSocketConnection(
                        it,
                        object : AirBeam3BleConnection.ConnectionListener {
                            override fun onDataReceived(
                                data: String,
                                device: BluetoothDevice
                            ) {
                                coroutineScope.launch {
                                    _sensorValues.emit(
                                        AirBeamSensorValueParser.parseLine(
                                            device.address,
                                            getBluetoothDeviceType(),
                                            data,
                                            DateTime()
                                        )
                                    )
                                }

                            }

                            override fun onDisconnected() {
                                coroutineScope.launch {
                                    this@AirBeam2Device.disconnect()
                                }
                            }

                        })
                    socketConnection?.connect { this@AirBeam2Device.onConnected() }
                    disconnect()
                } catch (e: Exception) {
                    disconnect()
                }
            }
        }
    }

    private fun closeConnection() {
        socketConnection?.closeConnection()
        socketConnection = null
    }

    override suspend fun disconnect() {
        closeConnection()
        onDisconnected()
    }

    override fun getBluetoothDeviceType(): BluetoothDeviceType {
        return BluetoothDeviceType.AIRBEAM2
    }
}