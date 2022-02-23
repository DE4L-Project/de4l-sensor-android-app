package io.de4l.app.device

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.sensor.SensorValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.properties.Delegates

@Entity
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(index = true)
    var name: String?,

    @ColumnInfo(index = true)
    val macAddress: String,

    @ColumnInfo
    var bluetoothDeviceType: BluetoothDeviceType,


    @ColumnInfo
    var targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
) {

    @ColumnInfo
    var actualConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
        set(value) {
            field = value
            this.actualConnectionStateFlow.value = value
        }

    @Ignore
    val actualConnectionStateFlow: MutableStateFlow<BluetoothConnectionState?> =
        MutableStateFlow(null)

    @Ignore
    val sensorValues: MutableStateFlow<SensorValue?> = MutableStateFlow(null)
}