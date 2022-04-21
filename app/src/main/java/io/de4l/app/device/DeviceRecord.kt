package io.de4l.app.device

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType

@Entity
data class DeviceRecord(
    @PrimaryKey(autoGenerate = true)
    var id: Long?,

    @ColumnInfo(index = true)
    var name: String,

    @ColumnInfo(index = true)
    val macAddress: String,

    @ColumnInfo
    var bluetoothDeviceType: BluetoothDeviceType,

    @ColumnInfo
    var targetConnectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,

    @ColumnInfo
    var versionUUID: String? = null
) {

}