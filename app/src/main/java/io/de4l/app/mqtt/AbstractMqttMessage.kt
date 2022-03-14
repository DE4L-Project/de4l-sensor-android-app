package io.de4l.app.mqtt

import com.google.gson.JsonObject
import org.joda.time.DateTime

abstract class AbstractMqttMessage(
    var username: String? = null,
    val appVersionCode: String,
    val mqttTopic: String,
    val trackingSessionId: String
) {

    abstract fun getTimestamp(): DateTime

    open fun toJson(): JsonObject {
        val messageJsonObj = JsonObject()
        messageJsonObj.addProperty("timestamp", getTimestamp().toString())
        messageJsonObj.addProperty("appVersionCode", appVersionCode)
        messageJsonObj.addProperty("username", username)
        messageJsonObj.addProperty("trackingSessionId", trackingSessionId)
        messageJsonObj.addProperty("schemaVersion", getSchemaVersion())
        return messageJsonObj
    }

    abstract fun getSchemaVersion(): String

}