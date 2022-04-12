package io.de4l.app.device

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.bluetooth.StartBleScannerEvent
import io.de4l.app.sensor.RuuviTagParser
import io.de4l.app.sensor.SensorType
import io.de4l.app.sensor.SensorValue
import io.de4l.app.tracking.TrackingManager_Factory
import org.greenrobot.eventbus.EventBus
import org.joda.time.DateTime

class RuuviTagDevice(
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.NONE
) : DeviceEntity(macAddress, targetConnectionState) {

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
                                getBluetoothDeviceType(),
                                SensorType.TEMPERATURE,
                                tagData.temperature,
                                timestamp,
                                tagData.toString()
                            )
                            _sensorValues.value = SensorValue(
                                it.address,
                                getBluetoothDeviceType(),
                                SensorType.HUMIDITY,
                                tagData.humidity,
                                timestamp,
                                tagData.toString()
                            )
                            _sensorValues.value = SensorValue(
                                it.address,
                                getBluetoothDeviceType(),
                                SensorType.PRESSURE,
                                tagData.pressure.toDouble() / 100.0,
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

    override fun getBluetoothDeviceType(): BluetoothDeviceType {
        return BluetoothDeviceType.RUUVI_TAG
    }

    override suspend fun forceReconnect() {
        TODO("Not yet implemented")
    }

}