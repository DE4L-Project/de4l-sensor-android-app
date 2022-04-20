package io.de4l.app.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.util.Log
import io.de4l.app.bluetooth.event.*
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.LocationService
import io.de4l.app.tracking.BackgroundServiceWatcher
import io.de4l.app.tracking.TrackingManager
import io.de4l.app.ui.event.SendSensorValueMqttEvent
import io.de4l.app.ui.event.SensorValueReceivedEvent
import io.de4l.app.util.ObservableMap
import io.de4l.app.util.ObservableSet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.util.*
import javax.inject.Inject


@SuppressLint("MissingPermission")
class BluetoothDeviceManager @Inject constructor(
    val application: Application,
    val bluetoothScanner: BluetoothScanner,
    val locationService: LocationService,
    val deviceRepository: DeviceRepository,
    val trackingManager: TrackingManager,
    val backgroundServiceWatcher: BackgroundServiceWatcher
) {
    private val LOG_TAG: String = BluetoothDeviceManager::class.java.name
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        EventBus.getDefault().register(this)
    }

    fun reset() {
        coroutineScope.launch {
            bluetoothScanner.cancelDeviceDiscovery()
            disconnectAllDevices()
        }
    }

    suspend fun hasConnectedDevices(): Flow<Boolean> {
        return this.deviceRepository
            .getDevices()
            .transform { devices ->
                var hasConnectedDevices = false
                for (device in devices) {
                    if (device._targetConnectionState.value == BluetoothConnectionState.CONNECTED) {
                        hasConnectedDevices = true
                        break;
                    }
                }
                emit(hasConnectedDevices)
            }
    }

    suspend fun connectDevice(macAddress: String) {
        connect(macAddress)
    }

    suspend fun connectDeviceWithRetry(macAddress: String) {
        connect(macAddress, true)
    }

    private suspend fun connect(macAddress: String, connectWithRetry: Boolean = false) {
        val deviceEntity = deviceRepository.getByAddress(macAddress).firstOrNull()
        deviceEntity?.let { _deviceEntity ->
            _deviceEntity._targetConnectionState.value = BluetoothConnectionState.CONNECTED
            onConnecting(_deviceEntity)
            val bluetoothDevice = bluetoothScanner.findBtDevice(macAddress, connectWithRetry)
            bluetoothDevice?.let { _bluetoothDevice ->
                _deviceEntity.bluetoothDevice = _bluetoothDevice
                _deviceEntity.connect()
            }
        }
    }


    suspend fun disconnectAllDevices() {
        val connectedDevices = deviceRepository.getDevices().firstOrNull()
        connectedDevices?.forEach {
            disconnect(it)
        }
    }

    fun disconnect(device: DeviceEntity) {
        Log.i(
            LOG_TAG,
            "BleDeviceTest - BluetoothDeviceManager::disconnect - ${Thread.currentThread().name}"
        )
        coroutineScope.launch {
            bluetoothScanner.stopSearchForDevice(device._macAddress.value)
            device._targetConnectionState.value = BluetoothConnectionState.DISCONNECTED
            device.disconnect()
        }

    }

    fun disconnect(device: BluetoothDevice) {
        coroutineScope.launch {
            val deviceEntity =
                deviceRepository.getByAddress(device.address).filterNotNull().first()
            disconnect(deviceEntity)
        }
    }


//    private suspend fun findBtDeviceWithRetry(macAddress: String): BluetoothDevice? {
//        var discoveredDevice: BluetoothDevice? = null
//        runWithRetry {
//            try {
//                discoveredDevice = findBtDevice(macAddress)
//                //Only retry if user did not stop search
//                if (_wantedDevices.contains(macAddress) && discoveredDevice == null) {
//                    throw RetryException("No device found; ${macAddress}")
//                }
//            } catch (e: BluetoothAlreadyScanningException) {
////                cancelDeviceDiscovery()
////                throw RetryException("Bluetooth Already Scanning")
//            }
//
//        }
//        return discoveredDevice
//    }

//    @ExperimentalCoroutinesApi
//    private suspend fun findBtDevice(macAddress: String): BluetoothDevice? {
//        Log.v(LOG_TAG, "findBtDevice: ${macAddress}")
//
//        var discoveredDevice: BluetoothDevice? = null
//        val bluetoothScanJob = BluetoothScanJob(macAddress)
//        scanJobQueue.emit(bluetoothScanJob)
//        discoveredDevice = bluetoothScanJob.waitForDevice()
//        Log.v(LOG_TAG, "Discovered Device: ${macAddress} - ${discoveredDevice?.address}")
//        return discoveredDevice
//    }

    private suspend fun onConnected(device: DeviceEntity) {
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - CONNECTED")
//        device._actualConnectionState.value = BluetoothConnectionState.CONNECTED
        saveDevice(device)
        EventBus.getDefault().post(BluetoothDeviceConnectedEvent(device))
    }

    private suspend fun onConnected(macAddress: String) {
        val device = deviceRepository.getByAddress(macAddress).firstOrNull()
        device?.let {
            onConnected(it)
        }
    }

    private suspend fun onConnecting(device: DeviceEntity) {
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - CONNECTING")
        if (device._actualConnectionState.value != BluetoothConnectionState.RECONNECTING) {
            device._actualConnectionState.value = BluetoothConnectionState.CONNECTING
        }
        saveDevice(device)

    }

    private suspend fun onConnecting(macAddress: String) {
        val device = deviceRepository.getByAddress(macAddress).firstOrNull()
        device?.let {
            onConnecting(it)
        }
    }

    private suspend fun onReconnecting(device: DeviceEntity) {
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - RECONNECTING")
        if (device._actualConnectionState.value != BluetoothConnectionState.CONNECTING) {
            device._actualConnectionState.value = BluetoothConnectionState.RECONNECTING
        }
        saveDevice(device)
    }

    private suspend fun onReconnecting(macAddress: String) {
        val device = deviceRepository.getByAddress(macAddress).firstOrNull()
        device?.let {
            onReconnecting(it)
        }
    }

    private suspend fun onDisconnected(device: DeviceEntity) {
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - DISCONNECTED")
        saveDevice(device)

        if (device._targetConnectionState.value == BluetoothConnectionState.DISCONNECTED) {
            bluetoothScanner.stopSearchForDevice(device._macAddress.value)
        }

        if (device._targetConnectionState.value == BluetoothConnectionState.CONNECTED) {
            onReconnecting(device)
            device._macAddress.value?.let {
                Log.v(LOG_TAG, "Connect via background service: ${it}")
                backgroundServiceWatcher.sendEventToService(ConnectToBluetoothDeviceEvent(it))
            }
        }
    }

    private suspend fun onDisconnected(macAddress: String) {
        val device = deviceRepository.getByAddress(macAddress).firstOrNull()
        device?.let {
            onDisconnected(it)
        }
    }

    private suspend fun saveDevice(device: DeviceEntity) {
        deviceRepository.updateDevice(device)
    }

    @Subscribe
    fun onBtDeviceConnectionChange(event: BtDeviceConnectionChangeEvent) {
        coroutineScope.launch {
            val deviceEntity = event.deviceEntity
            val connectionState = deviceEntity._actualConnectionState.value

            Log.v(
                LOG_TAG,
                "${deviceEntity._macAddress.value} | Connection Changed: ${connectionState}"
            )

            when (connectionState) {
                BluetoothConnectionState.CONNECTED -> onConnected(deviceEntity)
//                BluetoothConnectionState.CONNECTING -> onConnecting(deviceEntity)
//                BluetoothConnectionState.RECONNECTING -> onReconnecting(deviceEntity)
                BluetoothConnectionState.DISCONNECTED -> onDisconnected(deviceEntity)
            }
        }
    }

    @Subscribe
    fun onSensorValueReceivedEvent(event: SensorValueReceivedEvent) {
        event.sensorValue?.let {
            it.location = locationService.getCurrentLocation()
            it.sequenceNumber = trackingManager.messageNumber.getAndIncrement()
            EventBus.getDefault().post(SendSensorValueMqttEvent(it))
        }
    }

    companion object {
        fun getDeviceTypeForBluetoothDevice(bluetoothDevice: BluetoothDevice): BluetoothDeviceType {
            when {
                bluetoothDevice.name.startsWith("Airbeam2") -> {
                    return BluetoothDeviceType.AIRBEAM2
                }
                bluetoothDevice.name.startsWith("AirBeam3") -> {
                    return BluetoothDeviceType.AIRBEAM3
                }
                bluetoothDevice.name.startsWith("Ruuvi") -> {
                    return BluetoothDeviceType.RUUVI_TAG
                }
                else -> throw Exception("Unknown device type: ${bluetoothDevice.name}")
            }
        }
    }
}