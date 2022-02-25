package io.de4l.app.device

import androidx.databinding.BaseObservable
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.sensor.SensorValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.merge

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceEntity : BaseObservable {

    val _name: MutableStateFlow<String?> = MutableStateFlow(null)
    val _macAddress: MutableStateFlow<String?> = MutableStateFlow(null)

    val _bluetoothDeviceType: MutableStateFlow<BluetoothDeviceType> =
        MutableStateFlow(BluetoothDeviceType.NONE)

    val _targetConnectionState: MutableStateFlow<BluetoothConnectionState> =
        MutableStateFlow(BluetoothConnectionState.DISCONNECTED)
    val _actualConnectionState: MutableStateFlow<BluetoothConnectionState> =
        MutableStateFlow(BluetoothConnectionState.DISCONNECTED)

    val _sensorValues: MutableStateFlow<SensorValue?> = MutableStateFlow(null)


    val changes: Flow<Any?>

    init {
        changes = merge(
            _name,
            _macAddress,
            _bluetoothDeviceType,
            _targetConnectionState,
            _actualConnectionState,
            _sensorValues
        )
    }

    constructor(
        name: String?,
        macAddress: String,
        bluetoothDeviceType: BluetoothDeviceType,
        targetConnectionState: BluetoothConnectionState
    ) {
        this._name.value = name
        this._macAddress.value = macAddress
        this._bluetoothDeviceType.value = bluetoothDeviceType
        this._targetConnectionState.value = targetConnectionState
    }


//    val actualConnectionStateFlow: MutableStateFlow<BluetoothConnectionState?> =
//        MutableStateFlow(null)


}