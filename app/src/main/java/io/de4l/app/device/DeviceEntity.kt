package io.de4l.app.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.bluetooth.BluetoothSocketConnection
import io.de4l.app.sensor.SensorValue
import io.de4l.app.ui.event.SensorValueReceivedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import java.lang.Exception

@OptIn(ExperimentalCoroutinesApi::class)
abstract class DeviceEntity {
    val _name: MutableStateFlow<String?> = MutableStateFlow(null)
    val _macAddress: MutableStateFlow<String?> = MutableStateFlow(null)

    val _targetConnectionState: MutableStateFlow<BluetoothConnectionState> =
        MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    val _actualConnectionState: MutableStateFlow<BluetoothConnectionState> =
        MutableStateFlow(BluetoothConnectionState.DISCONNECTED)

    val _sensorValues: MutableStateFlow<SensorValue?> = MutableStateFlow(null)
    val changes: Flow<Any?>

    var bluetoothDevice: BluetoothDevice? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    constructor(
        name: String?,
        macAddress: String,
        targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
    ) {
        this._name.value = name
        this._macAddress.value = macAddress
        this._targetConnectionState.value = targetConnectionState

        changes = merge(
            _name,
            _macAddress,
            _targetConnectionState,
            _actualConnectionState,
            _sensorValues
        )

        coroutineScope.launch {
            _sensorValues.collect {
                EventBus.getDefault().post(SensorValueReceivedEvent(it))
            }
        }
    }

    abstract suspend fun forceReconnect()
    abstract suspend fun connect()
    abstract suspend fun disconnect()

    protected fun onConnecting() {
        this._actualConnectionState.value = BluetoothConnectionState.CONNECTING
    }

    protected fun onConnected() {
        this._actualConnectionState.value = BluetoothConnectionState.CONNECTED
    }

    protected fun onDisconnected() {
        this._actualConnectionState.value = BluetoothConnectionState.DISCONNECTED
    }

    protected fun onReconnecting() {
        this._actualConnectionState.value = BluetoothConnectionState.RECONNECTING
    }

    @SuppressLint("MissingPermission")
    companion object {

        fun fromBluetoothDevice(bluetoothDevice: BluetoothDevice): DeviceEntity {
            var deviceEntity: DeviceEntity
            when {
                isSensorBeacon(bluetoothDevice) -> deviceEntity =
                    BleDevice(bluetoothDevice.name, bluetoothDevice.address)
                isBleDevice(bluetoothDevice) -> deviceEntity =
                    BleDevice(bluetoothDevice.name, bluetoothDevice.address)
                else -> deviceEntity = LegacyBtDevice(bluetoothDevice.name, bluetoothDevice.address)
            }
            deviceEntity.bluetoothDevice = bluetoothDevice
            return deviceEntity
        }

        fun fromDeviceRecord(deviceRecord: DeviceRecord): DeviceEntity {
            var deviceEntity: DeviceEntity
            when (deviceRecord.bluetoothDeviceType) {
                BluetoothDeviceType.BLE ->
                    deviceEntity = BleDevice(deviceRecord.name, deviceRecord.macAddress)

                BluetoothDeviceType.LEGACY_BLUETOOTH ->
                    deviceEntity = LegacyBtDevice(
                        deviceRecord.name, deviceRecord.macAddress
                    )
                else ->
                    throw Exception("Unknown device type: ${deviceRecord.bluetoothDeviceType}")
            }

            deviceEntity._targetConnectionState.value = deviceRecord.targetConnectionState
            return deviceEntity
        }


        private fun isBleDevice(bluetoothDevice: BluetoothDevice): Boolean {
            return bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_LE || bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_DUAL
        }

        private fun isSensorBeacon(bluetoothDevice: BluetoothDevice): Boolean {
            return bluetoothDevice.name?.startsWith("Ruuvi") == true
        }
    }
}