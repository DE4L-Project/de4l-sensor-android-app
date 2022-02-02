package io.de4l.app.sensor

import io.de4l.app.BuildConfig
import io.de4l.app.location.Location
import io.de4l.app.tracking.TrackingManager
import org.joda.time.DateTime

class SensorValueParser(private val trackingManager: TrackingManager) {

    fun parseLine(
        deviceId: String,
        line: String,
        location: Location?,
        timestamp: DateTime
    ): SensorValue {
        val data = line.split(";")

        var sensorValue: Double? = data[0].toDoubleOrNull()
        val airBeamId = deviceId;
        val sensorType: SensorType? = parseSensorType(data[2])

        //Transform temperature to celsius
        if (sensorType === SensorType.TEMPERATURE && sensorValue != null) {
            sensorValue = (sensorValue - 32.0f) * (5.0f / 9.0f)
        }

        return SensorValue(
            airBeamId,
            sensorType,
            sensorValue,
            location,
            timestamp,
            trackingManager.messageNumber.getAndIncrement(),
            line,
            BuildConfig.VERSION_CODE.toString()
        )
    }

    private fun parseSensorType(sensorTypeRaw: String): SensorType? {
        return when (sensorTypeRaw) {
            "AirBeam2-F", "AirBeam3-F" -> SensorType.TEMPERATURE
            "AirBeam2-RH", "AirBeam3-RH" -> SensorType.HUMIDITY
            "AirBeam2-PM1", "AirBeam3-PM1" -> SensorType.PM1
            "AirBeam2-PM2.5", "AirBeam3-PM2.5" -> SensorType.PM2_5
            "AirBeam2-PM10", "AirBeam3-PM10" -> SensorType.PM10
            else -> null
        }
    }
}