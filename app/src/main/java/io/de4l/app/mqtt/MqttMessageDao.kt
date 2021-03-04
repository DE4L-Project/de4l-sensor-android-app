package io.de4l.app.mqtt

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MqttMessageDao {

    @Query("SELECT * FROM PersistentMqttMessage WHERE messageId = :messageId")
    fun getByMessageId(messageId: String?): PersistentMqttMessage

    @Query("SELECT * FROM PersistentMqttMessage")
    fun getAll(): List<PersistentMqttMessage>

    @Query("DELETE FROM PersistentMqttMessage WHERE messageId = :messageId")
    fun deleteByMessageId(messageId: String?)

    @Query("SELECT messageId FROM PersistentMqttMessage")
    fun getAllMessageIds(): List<String>

    @Query("DELETE FROM PersistentMqttMessage")
    fun deleteAll()

    @Query("SELECT COUNT (messageId) FROM PersistentMqttMessage WHERE messageId = :messageId")
    fun countForMessageId(messageId: String?) : Int

    @Insert
    fun insertAll(vararg mqttMessages: PersistentMqttMessage)

    @Delete
    fun delete(mqttMessage: PersistentMqttMessage)
}
