package io.de4l.app.device

import android.bluetooth.BluetoothDevice
import android.util.Log
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DeviceRepository(private val appDatabase: AppDatabase) {
    private val LOG_TAG = DeviceRepository::class.java.name

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

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
        return getDevices()
            .map { it.filter { device -> device.connectionState == BluetoothConnectionState.CONNECTED } }
    }

    suspend fun getDevices(): Flow<List<DeviceEntity>> {
        return getDevicesFromDb()
    }

    suspend fun addDevice(device: DeviceEntity) {
        appDatabase.deviceDao().insertAll(device)
    }

    suspend fun saveDevice(device: DeviceEntity) {
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
        return bluetoothDevice.name?.startsWith("Airbeam2") == true || bluetoothDevice.name?.startsWith("Ruuvi") == true
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
}