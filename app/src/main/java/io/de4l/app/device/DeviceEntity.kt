package io.de4l.app.device

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.sensor.SensorValue
import kotlinx.coroutines.flow.MutableStateFlow

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
    var actualConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,

    @ColumnInfo
    var targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED

) {

    @Ignore
    val sensorValues: MutableStateFlow<SensorValue?> = MutableStateFlow(null)

}