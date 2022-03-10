package io.de4l.app.mqtt

import com.google.gson.JsonObject
import io.de4l.app.sensor.SensorValue
import org.joda.time.DateTime

class SensorValueMqttMessage(
    private val sensorValue: SensorValue,
    username: String,
    appVersionCode: String,
    mqttTopic: String,
    trackingSessionId: String
) : AbstractMqttMessage(username, appVersionCode, mqttTopic, trackingSessionId) {

    override fun toJson(): JsonObject {
        val sensorValueJsonObj = super.toJson()

        sensorValueJsonObj.addProperty("airBeamId", this.sensorValue.airBeamId)
        sensorValueJsonObj.addProperty("sensorType", this.sensorValue.sensorType.toString())
        sensorValueJsonObj.addProperty("value", this.sensorValue.value)
        sensorValueJsonObj.addProperty("sequenceNumber", this.sensorValue.sequenceNumber)
        sensorValueJsonObj.addProperty("raw", this.sensorValue.rawData)

        var locationJsonObj: JsonObject? = null

        if (sensorValue.location != null) {
            locationJsonObj = JsonObject()

            //Location JSON Object can only contain lat and lon
            locationJsonObj.addProperty("lat", sensorValue.location?.latitude)
            locationJsonObj.addProperty("lon", sensorValue.location?.longitude)
            sensorValueJsonObj.add("location", locationJsonObj)
            sensorValueJsonObj.add("location", locationJsonObj)

            sensorValueJsonObj.addProperty("gpsAccuracy", sensorValue.location?.accuracy)
            sensorValueJsonObj.addProperty("altitudeInMeters", sensorValue.location?.altitude)
            sensorValueJsonObj.addProperty("bearingInDegrees", sensorValue.location?.bearing)
            sensorValueJsonObj.addProperty("speedInMetersPerSecond", sensorValue.location?.speed)
            sensorValueJsonObj.addProperty("provider", sensorValue.location?.provider)
        }

        return sensorValueJsonObj
    }

    override fun getTimestamp(): DateTime {
        return this.sensorValue.timestamp
    }
}