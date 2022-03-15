package io.de4l.app.device

import android.bluetooth.BluetoothDevice
import io.de4l.app.De4lApplication
import io.de4l.app.bluetooth.AirBeam3BleConnection
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.sensor.AirBeamSensorValueParser
import io.de4l.app.util.RetryException
import no.nordicsemi.android.ble.BleManager
import org.joda.time.DateTime
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AirBeam3Device(
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
) : BleDevice(macAddress, targetConnectionState) {

    override fun getBleConnection(cont: Continuation<Void?>): BleManager {
        return AirBeam3BleConnection(De4lApplication.context, object :
            AirBeam3BleConnection.ConnectionListener {
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
                    AirBeamSensorValueParser.parseLine(
                        device.address,
                        getBluetoothDeviceType(),
                        data,
                        DateTime()
                    )
            }
        })
    }

    override fun getBluetoothDeviceType(): BluetoothDeviceType {
        return BluetoothDeviceType.AIRBEAM3
    }
}