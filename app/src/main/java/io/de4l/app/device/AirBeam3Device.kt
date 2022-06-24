package io.de4l.app.device

import android.bluetooth.BluetoothDevice
import io.de4l.app.De4lApplication
import io.de4l.app.bluetooth.AirBeam3BleConnection
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.sensor.AirBeamSensorValueParser
import io.de4l.app.util.RetryException
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.BleManager
import org.joda.time.DateTime
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AirBeam3Device(
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.NONE
) : BleDevice(macAddress, targetConnectionState) {

    override fun getBleConnection(): BleManager {
        return AirBeam3BleConnection(De4lApplication.context, object :
            AirBeam3BleConnection.ConnectionListener {
            override fun onDisconnected() {
                this@AirBeam3Device.onDisconnected()
            }

            override fun onDataReceived(data: String, device: BluetoothDevice) {
                coroutineScope.launch {
                    val sensorValue = AirBeamSensorValueParser.parseLine(
                        device.address,
                        getBluetoothDeviceType(),
                        data,
                        DateTime()
                    )

                    sensorValue?.let {
                        _sensorValues.emit(sensorValue)
                    }
                }
            }
        })
    }

    override fun getBluetoothDeviceType(): BluetoothDeviceType {
        return BluetoothDeviceType.AIRBEAM3
    }
}