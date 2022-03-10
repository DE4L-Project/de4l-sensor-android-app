package io.de4l.app.sensor

import com.google.gson.JsonObject
import io.de4l.app.location.LocationValue
import io.de4l.app.mqtt.AbstractMqttMessage
import org.joda.time.DateTime

class SensorValue(
    val airBeamId: String,
    val sensorType: SensorType?,
    val value: Double?,
    val timestamp: DateTime,
    val rawData: String
) {
    var sequenceNumber: Long? = null
    var location: LocationValue? = null
}