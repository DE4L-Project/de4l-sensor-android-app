package io.de4l.app.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.util.Log
import io.de4l.app.util.ByteConverter
import org.joda.time.DateTime
import java.util.*


class BleGattCallback : BluetoothGattCallback() {

    private val LOG_TAG: String = BleGattCallback::class.java.name
    private val CONFIGURATION_CHARACTERISTIC_UUID =
        UUID.fromString("0000ffde-0000-1000-8000-00805f9b34fb")

    private val BEGIN_MESSAGE_CODE = 0xfe.toByte()
    private val END_MESSAGE_CODE = 0xff.toByte()
    private val BLUETOOTH_STREAMING_METHOD_CODE = 0x01.toByte()
    private val CURRENT_TIME_CODE = 0x08.toByte()

    private val MAX_MTU = 517
    private val DATE_FORMAT = "dd/MM/yy-HH:mm:ss"

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        super.onCharacteristicChanged(gatt, characteristic)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            gatt?.discoverServices()
        }
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorRead(gatt, descriptor, status)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
    }

    override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
        super.onMtuChanged(gatt, mtu, status)
    }

    override fun onPhyRead(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        super.onPhyRead(gatt, txPhy, rxPhy, status)
    }

    override fun onPhyUpdate(gatt: BluetoothGatt?, txPhy: Int, rxPhy: Int, status: Int) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status)
    }

    override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
        super.onReadRemoteRssi(gatt, rssi, status)
    }

    override fun onReliableWriteCompleted(gatt: BluetoothGatt?, status: Int) {
        super.onReliableWriteCompleted(gatt, status)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        gatt?.services?.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i(
                "printGattTable",
                "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )

        }

        gatt?.let {
            val service = it.getService(UUID.fromString("0000ffdd-0000-1000-8000-00805f9b34fb"))

            val configurationCharacteristic =
                service.getCharacteristic(CONFIGURATION_CHARACTERISTIC_UUID)

            configurationCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            configurationCharacteristic.value =
                byteArrayOf(
                    BEGIN_MESSAGE_CODE,
                    CURRENT_TIME_CODE
                )
                    .plus(ByteConverter.asciiToBytArray(DateTime.now().toString(DATE_FORMAT)))
                    .plus(END_MESSAGE_CODE)

            val dateWriteSuccess = it.writeCharacteristic(configurationCharacteristic)

            configurationCharacteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            configurationCharacteristic.value =
                byteArrayOf(BEGIN_MESSAGE_CODE, BLUETOOTH_STREAMING_METHOD_CODE, END_MESSAGE_CODE)

            val writeSuccess = it.writeCharacteristic(configurationCharacteristic)

            val characteristic =
                service
                    .getCharacteristic(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))
            it.setCharacteristicNotification(characteristic, true);

            val descriptor =
                characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val success = gatt.writeDescriptor(descriptor)
            Log.i(LOG_TAG, "" + success)

        }


    }

}