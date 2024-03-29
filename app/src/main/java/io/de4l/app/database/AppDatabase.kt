package io.de4l.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.de4l.app.device.DeviceDao
import io.de4l.app.device.DeviceRecord
import io.de4l.app.mqtt.MqttMessageDao
import io.de4l.app.mqtt.PersistentMqttMessage

@Database(
    entities = arrayOf(DeviceRecord::class, PersistentMqttMessage::class),
    version = 13,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun mqttMessageDao(): MqttMessageDao
}