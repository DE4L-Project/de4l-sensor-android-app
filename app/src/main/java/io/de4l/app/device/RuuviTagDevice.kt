package io.de4l.app.device

import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.bluetooth.event.StartBleScannerEvent
import io.de4l.app.bluetooth.event.StopBleScannerEvent
import io.de4l.app.sensor.RuuviTagParser
import io.de4l.app.sensor.SensorType
import io.de4l.app.sensor.SensorValue
import io.de4l.app.util.LoggingHelper
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.joda.time.DateTime
import java.util.*
import kotlin.concurrent.timerTask

class RuuviTagDevice(
    macAddress: String,
    targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.NONE
) : DeviceEntity(macAddress, targetConnectionState) {

    private val LOG_TAG = RuuviTagDevice::class.java.name
    private val CONNECTION_TIMEOUT_MILLIS = 60000L

    private var leScanCallback: ScanCallback? = null
    private var connectionTimer: Timer? = null

    override suspend fun connect() {
        leScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                coroutineScope.launch {
                    result?.let { scanResult ->
                        bluetoothDevice?.let {
                            if (scanResult.device?.address == it.address) {
                                resetAndStartConnectionTimer()

                                val tagData =
                                    RuuviTagParser().parseFromRawFormat5(scanResult.scanRecord!!.bytes)

                                val timestamp = DateTime()
                                _sensorValues.emit(
                                    SensorValue(
                                        it.address,
                                        getBluetoothDeviceType(),
                                        SensorType.TEMPERATURE,
                                        tagData.temperature,
                                        timestamp,
                                        tagData.toString()
                                    )
                                )
                                _sensorValues.emit(
                                    SensorValue(
                                        it.address,
                                        getBluetoothDeviceType(),
                                        SensorType.HUMIDITY,
                                        tagData.humidity,
                                        timestamp,
                                        tagData.toString()
                                    )
                                )
                                _sensorValues.emit(
                                    SensorValue(
                                        it.address,
                                        getBluetoothDeviceType(),
                                        SensorType.PRESSURE,
                                        tagData.pressure.toDouble() / 100.0,
                                        timestamp,
                                        tagData.toString()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        leScanCallback?.let { leScanCallback ->
            _macAddress.value?.let { macAddress ->
                EventBus.getDefault().post(StartBleScannerEvent(leScanCallback, macAddress))
            }
        }
        onConnected()
        resetAndStartConnectionTimer()
    }

    private fun resetAndStartConnectionTimer() {
        connectionTimer?.cancel()
        connectionTimer = Timer()
        connectionTimer?.schedule(
            timerTask {
                coroutineScope.launch {
                    LoggingHelper.logWithCurrentThread(
                        LOG_TAG,
                        "Ruuvi Tag - Connection Timeout: ${_macAddress.value}"
                    )
                    disconnect()
                }
            }, CONNECTION_TIMEOUT_MILLIS
        )
    }

    override suspend fun disconnect() {
        connectionTimer?.cancel()
        connectionTimer = null

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