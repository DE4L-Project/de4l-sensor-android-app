package io.de4l.app.sensor

import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.location.LocationValue
import org.joda.time.DateTime

class SensorValue(
    val sensorId: String,
    val deviceType: BluetoothDeviceType,
    val sensorType: SensorType,
    val value: Double?,
    val timestamp: DateTime,
    val rawData: String
) {
    var sequenceNumber: Long? = null
    var location: LocationValue? = null
}