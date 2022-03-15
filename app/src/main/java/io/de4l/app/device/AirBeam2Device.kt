package io.de4l.app.device

import android.bluetooth.BluetoothDevice
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
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
) : DeviceEntity(macAddress, targetConnectionState) {

    private val LOG_TAG: String = AirBeam2Device::class.java.name

    var socketConnection: BluetoothSocketConnection? = null

    private var bluetoothConnectionJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    override suspend fun forceReconnect() {
        socketConnection?.simulateConnectionLoss()
    }

    override suspend fun connect() {
        disconnect()
        onConnecting()
        bluetoothConnectionJob = coroutineScope.launch(Dispatchers.IO) {
            bluetoothDevice?.let {
                runWithRetry {
                    try {
                        socketConnection = BluetoothSocketConnection(
                            it,
                            object : AirBeam3BleConnection.ConnectionListener {
                                override fun onDataReceived(
                                    data: String,
                                    device: BluetoothDevice
                                ) {
                                    _sensorValues.value = AirBeamSensorValueParser.parseLine(
                                        device.address,
                                        getBluetoothDeviceType(),
                                        data,
                                        DateTime()
                                    )
                                }

                                override fun onDisconnected() {
                                    onDisconnected()
                                }

                            })
                        socketConnection?.connect { onConnected() }
                        disconnect()
                    } catch (e: Exception) {
                        disconnect()
                        if (_targetConnectionState.value === BluetoothConnectionState.CONNECTED) {
                            onReconnecting()
                            throw RetryException(e.message)
                        }
                    }
                }
            }
        }
    }

    override suspend fun disconnect() {
        onDisconnected()
        socketConnection?.closeConnection()
        socketConnection = null
    }

    override fun getBluetoothDeviceType(): BluetoothDeviceType {
        return BluetoothDeviceType.AIRBEAM2
    }
}