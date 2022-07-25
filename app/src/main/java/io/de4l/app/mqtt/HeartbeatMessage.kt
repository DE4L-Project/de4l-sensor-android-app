package io.de4l.app.mqtt

import com.google.gson.JsonObject
import io.de4l.app.tracking.HeartbeatValue
import org.joda.time.DateTime

class HeartbeatMessage(
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
}