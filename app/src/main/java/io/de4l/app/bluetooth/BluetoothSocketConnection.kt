package io.de4l.app.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.google.common.io.CharStreams
import com.google.common.io.LineProcessor
import io.de4l.app.AppConstants
import io.de4l.app.bluetooth.event.BluetoothDataReceivedEvent
import kotlinx.coroutines.delay
import org.greenrobot.eventbus.EventBus
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.*

class BluetoothSocketConnection(private val device: BluetoothDevice) {
    private val LOG_TAG: String = BluetoothSocketConnection::class.java.name

    private val MOBILE_SESSION_MESSAGE =
        byteArrayOf(0xfe.toByte(), 0x01.toByte(), 0xff.toByte())

    private var socketClosedIntentionally = false

    private var connected = false

    private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(UUID.fromString(AppConstants.BT_AIRBEAM2_SOCKET_RF_COMM_UUID))
    }

    suspend fun connect(onConnect: () -> Unit) {
        Log.i(LOG_TAG, "connect")
        val result = socket?.use { socket ->
            try {
                socket.connect()
                delay(1000)
                if (!socket.isConnected) {
                    throw IOException("Socket not connected")
                }
                Log.i(LOG_TAG, "Connected to ${device.name}")
                configureMobileSession(socket.outputStream)

                val inputStreamReader = InputStreamReader(socket.inputStream)
                val lineProcessor = object : LineProcessor<Void> {
                    override fun processLine(line: String): Boolean {

                        // On start up AirBeam2 occasionally produces wrong lines -> force disconnects
                        if (!isLineValid(line)) {
                            throw IOException("Line is not parsable!")
                        }

                        //Assume successful connection when first line was parsed w/o error
                        if (!connected) {
                            connected = true
                            onConnect()
                        }

                        if (!socket.isConnected) {
                            //Stop processing when socket is disconnected
                            return false
                        }

                        EventBus.getDefault().post(BluetoothDataReceivedEvent(line, device))
//                        Log.i(LOG_TAG, line)
                        return true
                    }

                    override fun getResult(): Void? {
                        return null
                    }

                }

                CharStreams.readLines(inputStreamReader, lineProcessor)


            } catch (e: IOException) {
                // If socket was closed on purpose, ignore IOException
                if (socketClosedIntentionally) {
                    return
                }
                throw  BluetoothConnectionLostException(e.message)
            } catch (e: Exception) {
                Log.e(LOG_TAG, e.message, e)
            }
        }
        Log.v(LOG_TAG, result.toString())
    }

    private fun isLineValid(line: String): Boolean {
        // Example line: 1;AirBeam2:0011E4000528;AirBeam2 - PM10;Particulate Matter;PM;micrograms per cubic meter;µg / m³;0;20;50;100;200
        if (line.isBlank()) {
            return false
        } else {
            val data = line.split(";")
            if (data.size != 12) {
                return false
            }
            if (!data[1].startsWith("AirBeam2:")) {
                return false
            }
        }
        return true
    }

    fun closeConnection() {
        Log.v(LOG_TAG, "closeConnection -  Start")
        socketClosedIntentionally = true
        socket?.close()
        Log.v(LOG_TAG, "closeConnection -  End")
    }

    fun simulateConnectionLoss() {
        socket?.close()
    }

    private suspend fun configureMobileSession(outputStream: OutputStream) {
        sendMessageToDevice(MOBILE_SESSION_MESSAGE, outputStream)
    }

    private fun sendMessageToDevice(bytes: ByteArray, outputStream: OutputStream) {
        outputStream.write(bytes)
        outputStream.flush()
    }
}