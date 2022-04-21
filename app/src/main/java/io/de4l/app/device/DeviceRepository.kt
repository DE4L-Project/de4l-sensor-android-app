package io.de4l.app.device

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.HashMap

@SuppressLint("MissingPermission")
class DeviceRepository(private val appDatabase: AppDatabase) {
    private val LOG_TAG = DeviceRepository::class.java.name
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val _cachedDevices: MutableMap<String, CachedDevice> = HashMap()

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
        val devices = getDevices()
        Log.v(LOG_TAG, "Devices")
        return devices
            .map {
                Log.v(LOG_TAG, "Devices Filter")
                it.filter { device ->
                    device._targetConnectionState.value == BluetoothConnectionState.CONNECTED
                }
            }
    }

    suspend fun getDevices(): Flow<List<DeviceEntity>> {
        return getDevicesFromDb()
    }

    suspend fun addDevice(device: DeviceEntity) {
        device._targetConnectionState.value = BluetoothConnectionState.DISCONNECTED
        device._actualConnectionState.value = BluetoothConnectionState.DISCONNECTED
        val deviceRecord = createDeviceRecordFromDeviceEntity(device)

        val insertedIds = appDatabase
            .deviceDao()
            .insertAll(deviceRecord)

        //Need For Caching
        deviceRecord.id = insertedIds[0]

        device._macAddress.value?.let {
            _cachedDevices[it] = CachedDevice(device, deviceRecord)
        }

    }

    suspend fun updateDevice(device: DeviceEntity) {
//        when (device._actualConnectionState) {
//            BluetoothConnectionState.CONNECTED -> registerDevice(device)
//            else -> unregisterDevice(device)
//        }

        try {
            val deviceRecord = createDeviceRecordFromDeviceEntity(device)
            deviceRecord.versionUUID = UUID.randomUUID().toString()
            appDatabase.deviceDao().updateDevice(deviceRecord)
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
                    _cachedDevices[deviceRecord.macAddress]?.deviceEntity
                        ?: createDeviceEntityFromDeviceRecord(
                            deviceRecord
                        )
                }
            }
    }

    private suspend fun getDevicesFromDb(): Flow<List<DeviceEntity>> {
        Log.v(LOG_TAG, "Get devices from DB")
        return appDatabase
            .deviceDao()
            .getAll()
            .map {
                it.map { deviceRecord ->
                    var cachedDevice = _cachedDevices[deviceRecord.macAddress]
                    if (cachedDevice == null) {
                        val deviceEntity = createDeviceEntityFromDeviceRecord(deviceRecord)
                        cachedDevice = CachedDevice(deviceEntity, deviceRecord)
                        _cachedDevices[deviceEntity._macAddress.value as String] = cachedDevice
                    }
                    cachedDevice.deviceEntity
                }
            }
    }

    private fun createDeviceEntityFromDeviceRecord(deviceRecord: DeviceRecord): DeviceEntity {
        val deviceEntity = DeviceEntity.fromDeviceRecord(deviceRecord)
        coroutineScope.launch {
            merge(deviceEntity._targetConnectionState)
                .collect {
                    updateDevice(deviceEntity)
                }
        }
        return deviceEntity
    }

    private fun createDeviceRecordFromDeviceEntity(device: DeviceEntity): DeviceRecord {
        val id: Long? = _cachedDevices[device._macAddress.value]?.deviceRecord?.id
        return DeviceRecord(
            id,
            device._name.value ?: "null",
            device._macAddress.value!!,
            getBluetoothTypeForDeviceEntity(device),
            device._targetConnectionState.value
        )
    }

    private fun getBluetoothTypeForDeviceEntity(device: DeviceEntity): BluetoothDeviceType {
        return when (device) {
            is AirBeam3Device -> BluetoothDeviceType.AIRBEAM3
            is RuuviTagDevice -> BluetoothDeviceType.RUUVI_TAG
            is AirBeam2Device -> BluetoothDeviceType.AIRBEAM2
            else -> BluetoothDeviceType.NONE
        }
    }
}