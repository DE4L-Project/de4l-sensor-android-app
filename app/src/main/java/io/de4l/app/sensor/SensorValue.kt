package io.de4l.app.sensor

import com.google.gson.JsonObject
import io.de4l.app.location.Location
import org.joda.time.DateTime

open class SensorValue(
    val airBeamId: String,
    val sensorType: SensorType?,
    val value: Double?,
    val location: Location?,
    val timestamp: DateTime,
    val sequenceNumber: Long,
    val rawData: String,
    val appVersionCode: String,
    var username: String? = null
) {
    fun toJson(): JsonObject {
        val sensorValueJsonObj = JsonObject()
        sensorValueJsonObj.addProperty("airBeamId", this.airBeamId)
        sensorValueJsonObj.addProperty("sensorType", this.sensorType.toString())
        sensorValueJsonObj.addProperty("value", value)
        sensorValueJsonObj.addProperty("timestamp", timestamp.toString())
        sensorValueJsonObj.addProperty("sequenceNumber", sequenceNumber)
        sensorValueJsonObj.addProperty("raw", rawData)
        sensorValueJsonObj.addProperty("appVersionCode", appVersionCode)
        sensorValueJsonObj.addProperty("username", username)

        var locationJsonObj: JsonObject? = null

        if (location != null) {
            locationJsonObj = JsonObject()
            locationJsonObj.addProperty("lat", location.latitude)
            locationJsonObj.addProperty("lon", location.longitude)
        }
        sensorValueJsonObj.add("location", locationJsonObj)

        return sensorValueJsonObj
    }
}