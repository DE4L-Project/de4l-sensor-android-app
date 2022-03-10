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
        val locationJsonObj = super.toJson()
        locationJsonObj.addProperty("lat", this.locationValue.latitude)
        locationJsonObj.addProperty("lon", this.locationValue.longitude)
        locationJsonObj.addProperty("gpsAccuracy", this.locationValue.accuracy)
        locationJsonObj.addProperty("altitudeInMeters", this.locationValue.altitude)
        locationJsonObj.addProperty("bearingInDegrees", this.locationValue.bearing)
        locationJsonObj.addProperty("speedInMetersPerSecond", this.locationValue.speed)
        locationJsonObj.addProperty("provider", this.locationValue.provider)
        locationJsonObj.addProperty("timestamp", this.locationValue.timestamp.toString())
        return locationJsonObj
    }

}