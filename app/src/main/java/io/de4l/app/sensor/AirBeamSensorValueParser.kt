package io.de4l.app.sensor

import android.util.Log
import io.de4l.app.bluetooth.BluetoothDeviceType
import org.joda.time.DateTime

class AirBeamSensorValueParser {
    companion object {
        private val LOG_TAG = AirBeamSensorValueParser::class.java.name

        fun parseLine(
            deviceId: String,
            deviceType: BluetoothDeviceType,
            line: String,
            timestamp: DateTime
        ): SensorValue? {
            try {
                val data = line.split(";")

                var sensorValue: Double? = data[0].toDoubleOrNull()
                val airBeamId = deviceId;
                val sensorType: SensorType = parseSensorType(data[2])

                //Transform temperature to celsius
                if (sensorType === SensorType.TEMPERATURE && sensorValue != null) {
                    sensorValue = (sensorValue - 32.0f) * (5.0f / 9.0f)
                }

                return SensorValue(
                    airBeamId,
                    deviceType,
                    sensorType,
                    sensorValue,
                    timestamp,
                    line
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, e.message ?: "Unknown Error")
                return null
            }
        }

        private fun parseSensorType(sensorTypeRaw: String): SensorType {
            return when (sensorTypeRaw) {
                "AirBeam2-F", "AirBeam3-F" -> SensorType.TEMPERATURE
                "AirBeam2-RH", "AirBeam3-RH" -> SensorType.HUMIDITY
                "AirBeam2-PM1", "AirBeam3-PM1" -> SensorType.PM1
                "AirBeam2-PM2.5", "AirBeam3-PM2.5" -> SensorType.PM2_5
                "AirBeam2-PM10", "AirBeam3-PM10" -> SensorType.PM10
                else -> throw UnsupportedOperationException("Sensor type not implemented: $sensorTypeRaw")
            }
        }
    }
}