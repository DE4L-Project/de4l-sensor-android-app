package io.de4l.app.device

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM DeviceRecord")
    fun getAll(): Flow<List<DeviceRecord>>

    @Query("SELECT * FROM DeviceRecord WHERE macAddress = :macAddress")
    fun getByMacAddress(macAddress: String): Flow<DeviceRecord?>

    @Query("SELECT * FROM DeviceRecord WHERE name = :name")
    suspend fun getByName(name: String): DeviceRecord?

//    @Query("SELECT * FROM DeviceRecord WHERE actualConnectionState = :connectionState")
//    fun getConnectedDevices(connectionState: BluetoothConnectionState = BluetoothConnectionState.CONNECTED): Flow<List<DeviceRecord?>>

//    @Query("UPDATE DeviceRecord SET actualConnectionState = :connectionState")
//    fun resetConnections(connectionState: String = BluetoothConnectionState.DISCONNECTED.toString())

    @Update
    suspend fun updateDevice(device: DeviceRecord)

    @Insert
    suspend fun insertAll(vararg devices: DeviceRecord) : List<Long>

    @Delete
    suspend fun delete(devices: DeviceRecord)

    @Query("DELETE FROM DeviceRecord WHERE macAddress = :macAddress")
    fun deleteByMacAddress(macAddress: String)
}