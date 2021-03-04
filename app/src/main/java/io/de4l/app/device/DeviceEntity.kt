package io.de4l.app.device

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.de4l.app.bluetooth.BluetoothConnectionState

@Entity
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,

    @ColumnInfo(index = true)
    var name: String?,

    @ColumnInfo(index = true)
    val macAddress: String,

    @ColumnInfo
    var connectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED
)