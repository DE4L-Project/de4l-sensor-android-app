package io.de4l.app.database

import androidx.room.TypeConverter
import io.de4l.app.bluetooth.BluetoothConnectionState

class RoomConverters {

    @TypeConverter
    fun fromConnectionStateEnum(value: BluetoothConnectionState): String {
        return value.name
    }

    @TypeConverter
    fun toDeviceConnectionState(name: String): BluetoothConnectionState {
        return BluetoothConnectionState.valueOf(name)
    }
}