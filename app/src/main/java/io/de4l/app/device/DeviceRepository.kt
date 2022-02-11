package io.de4l.app.device

import android.bluetooth.BluetoothDevice
import android.util.Log
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.database.AppDatabase
import io.de4l.app.sensor.SensorValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DeviceRepository(private val appDatabase: AppDatabase) {
    private val LOG_TAG = DeviceRepository::class.java.name

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

//    private val _connectedDevices: MutableMap<String, DeviceEntity> = HashMap()
//
//    val connectedDevices: MutableStateFlow<List<DeviceEntity>> =
//        MutableStateFlow(ArrayList())

    init {
        //Set isConnected = false for all device on application start
        coroutineScope.launch {
            appDatabase.deviceDao().resetConnections()
        }
    }

    suspend fun hasConnectedDevices(): Flow<Boolean> {
        return getConnectedDevices().map { it.isNotEmpty() }
    }

    suspend fun getConnectedDevices(): Flow<List<DeviceEntity>> {
        return getDevices().map {
            it.filter { device ->
                device.actualConnectionState == BluetoothConnectionState.CONNECTED
            }
        }
    }

    suspend fun getDevicesShouldBeConnected(): Flow<List<DeviceEntity>> {
        return getDevices()
            .map {
                it.filter { device ->
                    device.targetConnectionState == BluetoothConnectionState.CONNECTED
                }
            }
    }

    suspend fun getDevices(): Flow<List<DeviceEntity>> {
        return getDevicesFromDb()
    }

    suspend fun addDevice(device: DeviceEntity) {
        appDatabase.deviceDao().insertAll(device)
    }

    suspend fun saveDevice(device: DeviceEntity) {
//        when (device.actualConnectionState) {
//            BluetoothConnectionState.CONNECTED -> registerDevice(device)
//            else -> unregisterDevice(device)
//        }

        try {
            appDatabase.deviceDao().updateDevice(device)
        } catch (e: Exception) {
            Log.e(LOG_TAG, e.message ?: "Unknown", e)
        }
    }

    suspend fun getByAddress(macAddress: String): Flow<DeviceEntity?> {
        return getByAddressFromDb(macAddress)
    }

    suspend fun containsDeviceAddress(macAddress: String): Boolean {
        return getByAddress(macAddress).first() != null
    }

    fun isDeviceSupported(bluetoothDevice: BluetoothDevice): Boolean {
        //Currently only AirBeam and Ruuvi Devices supported
        return bluetoothDevice.name?.startsWith("Airbeam2") == true
                || bluetoothDevice.name?.startsWith("AirBeam3") == true
                || bluetoothDevice.name?.startsWith("Ruuvi") == true
    }

    suspend fun removeByAddress(macAddress: String) {
        val device = getByAddressFromDb(macAddress)
            .filterNotNull()
            .first()

        appDatabase.deviceDao().delete(device)
    }

    private suspend fun getByAddressFromDb(macAddress: String): Flow<DeviceEntity?> {
        return appDatabase.deviceDao().getByMacAddress(macAddress)
    }

    private suspend fun getDevicesFromDb(): Flow<List<DeviceEntity>> {
        return appDatabase.deviceDao().getAll()
    }

//    private suspend fun registerDevice(device: DeviceEntity) {
//        this._connectedDevices[device.macAddress] = device
//        this.connectedDevices.emit(this._connectedDevices.values.toList())
//    }
//
//    private suspend fun unregisterDevice(device: DeviceEntity) {
//        this._connectedDevices.remove(device.macAddress)
//        this.connectedDevices.emit(this._connectedDevices.values.toList())
//    }

//    suspend fun sendUpdateForDevice(macAddress: String, sensorValue: SensorValue) {
//        _connectedDevices[macAddress]?.sensorValues?.emit(sensorValue)
//    }
}