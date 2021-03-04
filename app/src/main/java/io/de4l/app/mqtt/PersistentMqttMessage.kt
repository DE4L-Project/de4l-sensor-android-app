package io.de4l.app.mqtt

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class PersistentMqttMessage(
    @PrimaryKey
    val uuid: String,
    @ColumnInfo(index = true)
    val messageId: String?,
    @ColumnInfo
    val headerBytes: ByteArray,
    @ColumnInfo
    val headerLength: Int,
    @ColumnInfo
    val headerOffset: Int,
    @ColumnInfo
    val payloadBytes: ByteArray,
    @ColumnInfo
    val payloadLength: Int,
    @ColumnInfo
    val payloadOffset: Int
)