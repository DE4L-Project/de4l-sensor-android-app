package io.de4l.app.mqtt

import android.database.DatabaseErrorHandler
import com.google.gson.JsonObject
import io.de4l.app.location.LocationValue
import org.joda.time.DateTime
import java.sql.Timestamp

abstract class AbstractMqttMessage(
    var username: String? = null,
    val appVersionCode: String,
    val mqttTopic: String,
    val trackingSessionId: String
) {

    abstract fun getTimestamp(): DateTime;

    open fun toJson(): JsonObject {
        val messageJsonObj = JsonObject()
        messageJsonObj.addProperty("timestamp", getTimestamp().toString())
        messageJsonObj.addProperty("appVersionCode", appVersionCode)
        messageJsonObj.addProperty("username", username)
        messageJsonObj.addProperty("trackingSessionId", trackingSessionId)
        return messageJsonObj
    }

}