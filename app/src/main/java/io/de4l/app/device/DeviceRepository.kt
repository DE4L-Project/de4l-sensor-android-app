package io.de4l.app.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.databinding.ObservableField
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*

@SuppressLint("MissingPermission")
class DeviceRepository(private val appDatabase: AppDatabase) {
    private val LOG_TAG = DeviceRepository::class.java.name
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val _connectedDevices: MutableMap<String, DeviceEntity> = HashMap()
//
//    val connectedDevices: MutableStateFlow<List<DeviceEntity>> =
//        MutableStateFlow(ArrayList())

//    init {
//        coroutineScope.launch {
//            appDatabase.deviceDao().resetConnections()
//        }
//    }

    suspend fun hasConnectedDevices(): Flow<Boolean> {
        return getConnectedDevices().map { it.isNotEmpty() }
    }

    suspend fun getConnectedDevices(): Flow<List<DeviceEntity>> {
        return getDevices().map {
            it.filter { device ->
                device._actualConnectionState.value == BluetoothConnectionState.CONNECTED
            }
        }
    }

    suspend fun getDevicesShouldBeConnected(): Flow<List<DeviceEntity>> {
        return getDevices()
            .map {
                it.filter { device ->
                    device._targetConnectionState.value == BluetoothConnectionState.CONNECTED
                }
            }
    }

    suspend fun getDevices(): Flow<List<DeviceEntity>> {
        return getDevicesFromDb()
    }

    suspend fun addDevice(device: DeviceEntity) {
        appDatabase
            .deviceDao()
            .insertAll(createDeviceRecordFromDeviceEntity(device))

        device._macAddress.value?.let {
            _connectedDevices[it] = device
        }

    }

    suspend fun saveDevice(device: DeviceEntity) {
//        when (device.actualConnectionState) {
//            BluetoothConnectionState.CONNECTED -> registerDevice(device)
//            else -> unregisterDevice(device)
//        }

        try {
            appDatabase.deviceDao().updateDevice(createDeviceRecordFromDeviceEntity(device))
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
        appDatabase.deviceDao().deleteByMacAddress(macAddress)
    }

    private suspend fun getByAddressFromDb(macAddress: String): Flow<DeviceEntity?> {
        return appDatabase.deviceDao()
            .getByMacAddress(macAddress)
            .map { deviceRecord ->
                if (deviceRecord == null) {
                    null
                } else {
                    _connectedDevices[deviceRecord.macAddress]
                        ?: createDeviceEntityFromDeviceRecord(deviceRecord)
                }
            }
    }

    private suspend fun getDevicesFromDb(): Flow<List<DeviceEntity>> {
        return appDatabase
            .deviceDao()
            .getAll()
            .map {
                it.map { deviceRecord ->
                    var cachedDevice = _connectedDevices[deviceRecord.macAddress]
                    if (cachedDevice == null) {
                        cachedDevice = createDeviceEntityFromDeviceRecord(deviceRecord)
                        _connectedDevices[cachedDevice._macAddress.value as String] = cachedDevice
                    }
                    cachedDevice
                }
            }
    }

    private fun createDeviceEntityFromDeviceRecord(deviceRecord: DeviceRecord): DeviceEntity {
        return DeviceEntity(
            deviceRecord.name,
            deviceRecord.macAddress,
            deviceRecord.bluetoothDeviceType,
            deviceRecord.targetConnectionState
        )
    }

    private fun createDeviceRecordFromDeviceEntity(device: DeviceEntity): DeviceRecord {
        return DeviceRecord(
            null,
            device._name.value!!,
            device._macAddress.value!!,
            device._bluetoothDeviceType.value,
            device._targetConnectionState.value
        )
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