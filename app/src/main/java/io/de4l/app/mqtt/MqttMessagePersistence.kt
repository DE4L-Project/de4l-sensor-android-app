package io.de4l.app.mqtt

import android.util.Log
import io.de4l.app.database.AppDatabase
import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.MqttPersistable
import java.util.*
import javax.inject.Inject

class MqttMessagePersistence @Inject constructor(val appDatabase: AppDatabase) :
    MqttClientPersistence {

    private val LOG_TAG: String = MqttMessagePersistence::javaClass.name

    override fun close() {
        Log.i(LOG_TAG, "close")
    }

    override fun open(clientId: String?, serverURI: String?) {
        Log.i(LOG_TAG, "open")
    }

    override fun put(key: String?, persistable: MqttPersistable?) {
        Log.i(LOG_TAG, "put: ${key} [${getThreadName()}]")
        if (persistable != null) {
            val persistentMqttMessage: PersistentMqttMessage = PersistentMqttMessage(
                UUID.randomUUID().toString(),
                key,
                persistable.headerBytes,
                persistable.headerLength,
                persistable.headerOffset,
                persistable.payloadBytes,
                persistable.payloadLength,
                persistable.payloadOffset
            )
            appDatabase.mqttMessageDao().insertAll(persistentMqttMessage)
        }
    }

    override fun get(key: String?): MqttPersistable {
        Log.i(LOG_TAG, "get [${getThreadName()}]")
        val persistentMqttMessage: PersistentMqttMessage =
            appDatabase.mqttMessageDao().getByMessageId(key)

        return object : MqttPersistable {
            override fun getHeaderBytes(): ByteArray {
                return persistentMqttMessage.headerBytes
            }

            override fun getHeaderLength(): Int {
                return persistentMqttMessage.headerLength
            }

            override fun getHeaderOffset(): Int {
                return persistentMqttMessage.headerOffset
            }

            override fun getPayloadBytes(): ByteArray {
                return persistentMqttMessage.payloadBytes
            }

            override fun getPayloadLength(): Int {
                return persistentMqttMessage.payloadLength
            }

            override fun getPayloadOffset(): Int {
                return persistentMqttMessage.payloadOffset
            }

        }
    }

    override fun remove(key: String?) {
        Log.i(LOG_TAG, "remove [${getThreadName()}]")
        appDatabase.mqttMessageDao().deleteByMessageId(key)
    }

    override fun keys(): Enumeration<String> {
        Log.i(LOG_TAG, "keys [${getThreadName()}]")
        return Collections.enumeration(appDatabase.mqttMessageDao().getAllMessageIds())
    }

    override fun clear() {
        Log.i(LOG_TAG, "clear [${getThreadName()}]")
        appDatabase.mqttMessageDao().deleteAll()
    }

    override fun containsKey(key: String?): Boolean {
        Log.i(LOG_TAG, "containsKey [${getThreadName()}]")
        return appDatabase.mqttMessageDao().countForMessageId(key) != 0
    }

    private fun getThreadName(): String {
        return Thread.currentThread().name
    }
}