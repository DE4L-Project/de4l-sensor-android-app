package io.de4l.app.mqtt

import com.google.gson.JsonObject
import io.de4l.app.location.LocationValue
import org.joda.time.DateTime

class LocationMqttMessage(
    private val locationValue: LocationValue,
    username: String,
    appVersionCode: String,
    mqttTopic: String,
    trackingSessionId: String
) : AbstractMqttMessage(username, appVersionCode, mqttTopic, trackingSessionId) {

    override fun getTimestamp(): DateTime {
        return this.locationValue.timestamp
    }

    override fun toJson(): JsonObject {
        val messageObj = super.toJson();

        //Location JSON Object can only contain lat and lon
        val locationJsonObj = JsonObject()
        locationJsonObj.addProperty("lat", this.locationValue.latitude)
        locationJsonObj.addProperty("lon", this.locationValue.longitude)
        messageObj.add("location", locationJsonObj)

        messageObj.addProperty("gpsAccuracy", this.locationValue.accuracy)
        messageObj.addProperty("altitudeInMeters", this.locationValue.altitude)
        messageObj.addProperty("bearingInDegrees", this.locationValue.bearing)
        messageObj.addProperty("speedInMetersPerSecond", this.locationValue.speed)
        messageObj.addProperty("provider", this.locationValue.provider)
        messageObj.addProperty("timestamp", this.locationValue.timestamp.toString())
        return messageObj
    }

    override fun getSchemaVersion(): String {
        return "0.1.0"
    }

}