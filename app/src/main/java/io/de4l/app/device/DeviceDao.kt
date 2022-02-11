package io.de4l.app.device

import androidx.room.*
import io.de4l.app.bluetooth.BluetoothConnectionState
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM DeviceEntity")
    fun getAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM DeviceEntity WHERE macAddress = :macAddress")
    fun getByMacAddress(macAddress: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM DeviceEntity WHERE name = :name")
    suspend fun getByName(name: String): DeviceEntity?

    @Query("SELECT * FROM DeviceEntity WHERE actualConnectionState = :connectionState")
    fun getConnectedDevices(connectionState: BluetoothConnectionState = BluetoothConnectionState.CONNECTED): Flow<List<DeviceEntity?>>

    @Query("UPDATE DeviceEntity SET actualConnectionState = :connectionState")
    fun resetConnections(connectionState: String = BluetoothConnectionState.DISCONNECTED.toString())

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Insert
    suspend fun insertAll(vararg devices: DeviceEntity)

    @Delete
    suspend fun delete(devices: DeviceEntity)
}