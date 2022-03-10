package io.de4l.app.device

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.StartBleScannerEvent
import io.de4l.app.sensor.RuuviTagParser
import io.de4l.app.sensor.SensorType
import io.de4l.app.sensor.SensorValue
import org.greenrobot.eventbus.EventBus
import org.joda.time.DateTime

class RuuviTagDevice(
    name: String?,
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
) : DeviceEntity(name, macAddress, targetConnectionState) {

    private var leScanCallback: ScanCallback? = null

    override suspend fun connect() {
        onConnecting()
        leScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanResult ->
                    bluetoothDevice?.let {
                        if (scanResult.device?.address == it.address) {
                            val tagData =
                                RuuviTagParser().parseFromRawFormat5(scanResult.scanRecord!!.bytes)

                            val timestamp = DateTime()
                            _sensorValues.value = SensorValue(
                                it.address,
                                SensorType.TEMPERATURE,
                                tagData.temperature,
                                timestamp,
                                tagData.toString()
                            )
                            _sensorValues.value = SensorValue(
                                it.address,
                                SensorType.HUMIDITY,
                                tagData.humidity,
                                timestamp,
                                tagData.toString()
                            )
                            _sensorValues.value = SensorValue(
                                it.address,
                                SensorType.PRESSURE,
                                tagData.pressure.toDouble(),
                                timestamp,
                                tagData.toString()
                            )
                        }
                    }
                }
            }
        }
        leScanCallback?.let {
            EventBus.getDefault().post(StartBleScannerEvent(it))
        }
        onConnected()
    }

    override suspend fun disconnect() {
        leScanCallback?.let {
            EventBus.getDefault().post(StopBleScannerEvent(it))
        }
        leScanCallback = null
        onDisconnected()
    }

    override suspend fun forceReconnect() {
        TODO("Not yet implemented")
    }

}