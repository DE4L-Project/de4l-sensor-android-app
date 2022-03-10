package io.de4l.app.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import io.de4l.app.device.BleDevice
import io.de4l.app.device.DeviceEntity
import io.de4l.app.util.ByteConverter
import no.nordicsemi.android.ble.BleManager
import org.joda.time.DateTime
import java.util.*

class RuuviTagBleConnection(
    context: Context,
    private val connectionListener: ConnectionListener
) :
    BleManager(context) {

    private val LOG_TAG: String = BleGattCallback::class.java.name

    private val SERVICE_UUID = UUID.fromString("0000ffdd-0000-1000-8000-00805f9b34fb")
    private val CONFIGURATION_CHARACTERISTIC_UUID =
        UUID.fromString("0000ffde-0000-1000-8000-00805f9b34fb")

    private val BEGIN_MESSAGE_CODE = 0xfe.toByte()
    private val END_MESSAGE_CODE = 0xff.toByte()
    private val BLUETOOTH_STREAMING_METHOD_CODE = 0x01.toByte()
    private val CURRENT_TIME_CODE = 0x08.toByte()

    private val MAX_MTU = 517
    private val DATE_FORMAT = "dd/MM/yy-HH:mm:ss"

    private val MEASUREMENTS_CHARACTERISTIC_UUIDS = arrayListOf(
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),    // Temperature
        UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb"),    // Humidity
        UUID.fromString("0000ffe4-0000-1000-8000-00805f9b34fb"),    // PM1
        UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"),    // PM2.5
        UUID.fromString("0000ffe6-0000-1000-8000-00805f9b34fb")     // PM10
    )

    private var configurationCharacteristic: BluetoothGattCharacteristic? = null
    private var measurementsCharacteristics: List<BluetoothGattCharacteristic> = emptyList()

    override fun getGattCallback(): BleManagerGattCallback {
        return BleConnectionManagerGattCallback()
    }

    interface ConnectionListener {
        fun onDataReceived(data: String, device: BluetoothDevice)
        fun onDisconnected()
    }

    private inner class BleConnectionManagerGattCallback() :
        BleManagerGattCallback() {

        //        private var bleDeviceType: BleDeviceTypeEnum? = null
        private var device: DeviceEntity? = null;

        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            device = DeviceEntity.fromBluetoothDevice(gatt.device)

            val service = gatt.getService(SERVICE_UUID) ?: return false

            configurationCharacteristic =
                service.getCharacteristic(CONFIGURATION_CHARACTERISTIC_UUID)

            measurementsCharacteristics = MEASUREMENTS_CHARACTERISTIC_UUIDS.mapNotNull { uuid ->
                service.getCharacteristic(uuid)
            }
            return device != null
                    && device is BleDevice
                    && configurationCharacteristic != null
                    && measurementsCharacteristics.isNotEmpty()
        }

        override fun onServicesInvalidated() {
            //Null check in initialization
            device?.let {
                device = null;
            }

            configurationCharacteristic = null
            measurementsCharacteristics = emptyList()
            connectionListener.onDisconnected()
        }


        override fun initialize() {
            measurementsCharacteristics.forEach {
                setNotificationCallback(it)
                    .with { device, data ->
                        connectionListener.onDataReceived(String(data.value!!), device)
                    }
            }

            val dateByteArray = byteArrayOf(
                BEGIN_MESSAGE_CODE,
                CURRENT_TIME_CODE
            )
                .plus(ByteConverter.asciiToBytArray(DateTime.now().toString(DATE_FORMAT)))
                .plus(END_MESSAGE_CODE)

            val queue = beginAtomicRequestQueue()
            queue
                .add(
                    writeCharacteristic(
                        configurationCharacteristic,
                        dateByteArray,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                )
                .add(sleep(100))
                .add(requestMtu(MAX_MTU))
                .add(
                    writeCharacteristic(
                        configurationCharacteristic,
                        byteArrayOf(
                            BEGIN_MESSAGE_CODE,
                            BLUETOOTH_STREAMING_METHOD_CODE,
                            END_MESSAGE_CODE
                        ),
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                )
                .add(sleep(100))

            measurementsCharacteristics.forEach {
                queue
                    .add(enableNotifications(it))
                    .add(sleep(100))
            }

            queue.enqueue()
        }

    }

}