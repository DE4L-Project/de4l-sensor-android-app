package io.de4l.app.mqtt

import android.content.Context
import android.util.Log
import com.auth0.android.jwt.JWT
import com.google.common.collect.ImmutableList
import io.de4l.app.AppConstants
import io.de4l.app.auth.AuthManager
import io.de4l.app.util.RetryException
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import org.eclipse.paho.android.service.MqttAndroidClient

import org.eclipse.paho.client.mqttv3.*

class MqttManager(
    private val context: Context,
    private val mqttMessagePersistence: MqttMessagePersistence,
    private val authManager: AuthManager
) {

    // ( ) Persist own disconnect buffer
    // ( ) Reconnect when WiFi is available again

    private val LOG_TAG: String = MqttManager::class.java.name

    private var mqttAndroidClient: MqttAsyncClient? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var connectJob: Job? = null

    private val connectionState: MutableStateFlow<MqttConnectionState> =
        MutableStateFlow(MqttConnectionState.DISCONNECTED)

    private val buffer: MutableList<AbstractMqttMessage> = ArrayList()

    @ExperimentalCoroutinesApi
    private suspend fun connect(): Flow<Boolean> = callbackFlow<Boolean> {
        try {
            Log.v(LOG_TAG, "MQTT - Connect")

            if (mqttAndroidClient == null) {
                mqttAndroidClient = createMqttClient()
            }

            val mqttConnectOptions = MqttConnectOptions()
            val accessToken = authManager.getValidAccessToken()

            if (accessToken == null) {
                throw Exception("Access token was null!")
            }

            mqttConnectOptions.userName =
                JWT(accessToken)
                    .getClaim(AppConstants.AUTH_USERNAME_CLAIM_KEY)
                    .asString()

            mqttConnectOptions.password = accessToken.toCharArray()
            mqttConnectOptions.isCleanSession = false

            mqttAndroidClient?.connect(mqttConnectOptions, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.i(LOG_TAG, "MQTT - Connect succeed")
                    //must be set when connected

                    //Sometimes client in token is different from mqttAndroidClient (Race condition?)
                    //https://github.com/eclipse/paho.mqtt.android/issues/238#issuecomment-576289548
                    asyncActionToken.client?.setBufferOpts(getBufferOptions())

                    //buffer has sometimes null elements, unknown why
                    val bufferedMessages = ImmutableList.copyOf(buffer)
                    buffer.clear()

                    connectionState.value = MqttConnectionState.CONNECTED
                    coroutineScope.launch {
                        for (message in bufferedMessages) {
                            publishForCurrentUserSync(message)
                        }
                    }
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                    Log.i(LOG_TAG, "MQTT - Connection failed", exception)
                    close(MqttConnectFailedException(exception.message))
                }
            })
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message ?: "")
            close(MqttConnectFailedException(e.message))
        }
        awaitClose {
            cancel()
        }
    }

    private fun getBufferOptions(): DisconnectedBufferOptions {
        val bufferOptions = DisconnectedBufferOptions()
        bufferOptions.isBufferEnabled = true
        bufferOptions.isPersistBuffer = true
        bufferOptions.bufferSize = 99999
        return bufferOptions
    }

    @ExperimentalCoroutinesApi
    suspend fun connectWithRetry() {
        connectJob?.cancel()
        connectJob = coroutineScope.launch {
            runWithRetry {
                Log.v(LOG_TAG, "Before connect().firstOrNull()")
                try {
                    connect().firstOrNull()
                } catch (e: Exception) {
                    Log.v(LOG_TAG, e.message ?: "Unknown")
                    throw RetryException(e)
                }

                Log.v(LOG_TAG, "After connect().firstOrNull()")
            }
        }
    }

    fun disconnect() {
        connectionState.value = MqttConnectionState.DISCONNECTED
        try {
            //Throws error when not connected on disconnected -> does not matter
            mqttAndroidClient?.disconnect()
        } catch (e: Exception) {
            Log.v(LOG_TAG, e.message ?: "Error on MQTT Disconnect")
        }
    }

    @ExperimentalCoroutinesApi
    private fun createMqttClient(): MqttAsyncClient {
        val mqttClient =
            MqttAsyncClient(
                AppConstants.MQTT_SERVER_URL,
                MqttClient.generateClientId(),
                mqttMessagePersistence
            )


        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                try {
                    connectionState.value = MqttConnectionState.CONNECTION_LOST
                    mqttAndroidClient?.disconnect()
                    coroutineScope.launch {
                        delay(2000)
                        connectWithRetry()
                    }
                } catch (e: Exception) {
                    Log.v(LOG_TAG, "${e.message}")
                }
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.i(LOG_TAG, "messageArrived")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                Log.i(LOG_TAG, "deliveryComplete")
            }
        })

        return mqttClient
    }

    @ExperimentalCoroutinesApi
    private suspend fun publishForCurrentUserSync(message: AbstractMqttMessage) {
        authManager.user.value?.username?.let { username ->
            message.username = username
            publish(
                String.format(
                    message.mqttTopic,
                    username
                ), message
            ).firstOrNull()
        }
    }

    @ExperimentalCoroutinesApi
    fun publishForCurrentUser(message: AbstractMqttMessage) {
        coroutineScope.launch {
            publishForCurrentUserSync(message)
        }
    }

    @ExperimentalCoroutinesApi
    private suspend fun publish(topic: String, message: AbstractMqttMessage): Flow<Boolean> =
        callbackFlow {
            try {
                val mqttMessage = MqttMessage(message.toJson().toString().toByteArray())

                when (connectionState.value) {
                    MqttConnectionState.DISCONNECTED -> {
                        Log.v(LOG_TAG, "Publish called on disconnected client.")
                    }
                    MqttConnectionState.CONNECTION_LOST -> buffer.add(message)
                    MqttConnectionState.CONNECTED -> {
                        try {
                            val token = mqttAndroidClient?.publish(topic, mqttMessage)
                            token?.waitForCompletion(1000)
                            offer(true)
                        } catch (e: Exception) {
                            Log.v(LOG_TAG, e.message ?: e::class.java.name)
                        }
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
            awaitClose {
                cancel()
            }
        }
}