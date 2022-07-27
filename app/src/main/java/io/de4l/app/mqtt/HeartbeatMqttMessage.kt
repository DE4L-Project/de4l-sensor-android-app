package io.de4l.app.mqtt

import com.google.gson.JsonObject
import io.de4l.app.tracking.HeartbeatValue
import org.joda.time.DateTime

class HeartbeatMqttMessage(
    private val heartbeatValue: HeartbeatValue,
    username: String,
    appVersionCode: String,
    mqttTopic: String,
    trackingSessionId: String
) : AbstractMqttMessage(username, appVersionCode, mqttTopic, trackingSessionId) {

    override fun getTimestamp(): DateTime {
        return heartbeatValue.timestamp
    }

    override fun getSchemaVersion(): String {
        return "0.1.0"
    }

    override fun toJson(): JsonObject {
        val heartbeatValueObj = super.toJson()
        heartbeatValueObj.addProperty("batteryPercentage", this.heartbeatValue.batteryPercentage)
        heartbeatValueObj.addProperty("applicationId", this.heartbeatValue.appId)
        heartbeatValueObj.addProperty(
            "isPowerSaveModeEnabled",
            this.heartbeatValue.isPowerSaveMode
        )

        return heartbeatValueObj
    }
}