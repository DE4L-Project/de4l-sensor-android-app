package io.de4l.app.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.de4l.app.bluetooth.event.BluetoothDeviceConnectedEvent
import io.de4l.app.bluetooth.event.BluetoothDeviceDisconnectedEvent
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.device.StopBleScannerEvent
import io.de4l.app.location.LocationService
import io.de4l.app.sensor.RuuviTagParser
import io.de4l.app.tracking.TrackingManager
import io.de4l.app.ui.event.SendSensorValueMqttEvent
import io.de4l.app.ui.event.SensorValueReceivedEvent
import io.de4l.app.util.RetryException
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.joda.time.DateTime
import javax.inject.Inject

@SuppressLint("MissingPermission")
class BluetoothDeviceManager @Inject constructor(
    val application: Application,
    val bluetoothAdapter: BluetoothAdapter,
    val locationService: LocationService,
    val deviceRepository: DeviceRepository,
    val trackingManager: TrackingManager,
) {
    private val LOG_TAG: String = BluetoothDeviceManager::class.java.name
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var leScanCallback: ScanCallback? = null

    val bluetoothScanScanState = MutableStateFlow(BluetoothScanState.NOT_SCANNING)

    init {
        EventBus.getDefault().register(this)

    }

    fun startDeviceDiscovery() {
        bluetoothAdapter.startDiscovery()
    }

    fun cancelDeviceDiscovery() {
        bluetoothAdapter.cancelDiscovery()
    }

    fun reset() {
        coroutineScope.launch {
            cancelDeviceDiscovery()
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
//        onConnecting(macAddress)
        val bluetoothDevice =
            if (connectWithRetry) findBtDeviceWithRetry(macAddress) else findBtDevice(macAddress)

        bluetoothDevice?.let {
            val deviceEntity = deviceRepository.getByAddress(macAddress).firstOrNull()
            deviceEntity?.let {
                it._targetConnectionState.value = BluetoothConnectionState.CONNECTED
                it.bluetoothDevice = bluetoothDevice
                deviceEntity.connect()
            }
        }
        onDisconnected(macAddress)
    }

    private fun connectSensorBeacon(device: BluetoothDevice) {
        coroutineScope.launch {

            leScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let { scanResult ->
                        if (scanResult.device?.address == device.address) {
                            val tagData =
                                RuuviTagParser().parseFromRawFormat5(scanResult.scanRecord!!.bytes)
                            Log.v(LOG_TAG, tagData.toString())

                            val location = locationService.getCurrentLocation()
                            val timestamp = DateTime()

//                            EventBus.getDefault()
//                                .post(
//                                    SensorValueReceivedEvent(
//                                        SensorValue(
//                                            scanResult.device!!.address,
//                                            SensorType.TEMPERATURE,
//                                            tagData.temperature,
//                                            location,
//                                            timestamp,
//                                            trackingManager.messageNumber.getAndIncrement(),
//                                            "",
//                                        )
//                                    )
//                                )
//
//                            EventBus.getDefault()
//                                .post(
//                                    SensorValueReceivedEvent(
//                                        SensorValue(
//                                            scanResult.device!!.address,
//                                            SensorType.HUMIDITY,
//                                            tagData.humidity,
//                                            location,
//                                            timestamp,
//                                            trackingManager.messageNumber.getAndIncrement(),
//                                            "",
//                                        )
//                                    )
//                                )
                        }
                    }
                }
            }

            bluetoothAdapter.bluetoothLeScanner.startScan(leScanCallback)
            onSuccessfulConnect(device)
        }
    }


    //https://github.com/ThanosFisherman/BlueFlow
    @ExperimentalCoroutinesApi
    suspend fun scanForDevices() = callbackFlow<BluetoothDevice> {

        if (bluetoothScanScanState.value != BluetoothScanState.NOT_SCANNING) {
            Log.w(LOG_TAG, "Is already scanning.")
            close()
        }

        bluetoothScanScanState.value = BluetoothScanState.SCANNING

        val scanJobTimeout = launch {
            delay(20000)
            close()
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                        device?.let {
                            try {
                                trySend(device)
                            } catch (e: ClosedSendChannelException) {
                                Log.v(
                                    LOG_TAG,
                                    "Closed Channel Exception in BluetoothDeviceManager:onReceive - Can be ignored."
                                )
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        Log.v(LOG_TAG, "ACTION_DISCOVERY_STARTED")
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.v(LOG_TAG, "ACTION_DISCOVERY_FINISHED")
                        close()
                    }
                }
            }
        }

        application.registerReceiver(receiver, filter)
        startDeviceDiscovery()

        awaitClose {
            scanJobTimeout.cancel()
            application.unregisterReceiver(receiver)
            bluetoothScanScanState.value = BluetoothScanState.NOT_SCANNING
        }
    }
        .filterNotNull()
        .flowOn(Dispatchers.IO)

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
            device._targetConnectionState.value = BluetoothConnectionState.DISCONNECTED
            device.disconnect()
        }

//        when (device._bluetoothDeviceType.value) {
//            BluetoothDeviceType.LEGACY_BLUETOOTH -> {
//                closeLegacyBtConnection()
//                bluetoothConnectionJob?.cancel()
//                bluetoothConnectionJob = null
//            }
//            BluetoothDeviceType.BLE -> {
//                bleConnectionManager.disconnect().enqueue()
//            }
//            BluetoothDeviceType.BLE_BEACON -> {
//                leScanCallback?.let {
//                    bluetoothAdapter.bluetoothLeScanner.stopScan(it)
//                }
//                leScanCallback = null
//            }
//            else -> {
//                Log.i(
//                    LOG_TAG,
//                    "Trying to disconnect unknown BT DeviceType: ${device._macAddress.value} | ${device._name.value}"
//                )
//            }
//        }


//        coroutineScope.launch {
//            onDisconnected(device)
//        }
    }

    fun disconnect(device: BluetoothDevice) {
        coroutineScope.launch {
            val deviceEntity =
                deviceRepository.getByAddress(device.address).filterNotNull().first()
            disconnect(deviceEntity)
        }
    }


    private suspend fun findBtDeviceWithRetry(macAddress: String): BluetoothDevice? {
        var discoveredDevice: BluetoothDevice? = null
        runWithRetry {
            discoveredDevice = findBtDevice(macAddress)
            if (discoveredDevice == null) {
                throw RetryException("No device found")
            }
        }
        return discoveredDevice
    }

    @ExperimentalCoroutinesApi
    private suspend fun findBtDevice(macAddress: String): BluetoothDevice? {
        var discoveredDevice: BluetoothDevice? = null
        val job = coroutineScope.async {
            scanForDevices()
                .filter {
                    it.address == macAddress
                }
                .collect {
                    cancelDeviceDiscovery()
                    Log.i(LOG_TAG, "Device: ${it.address}")
                    discoveredDevice = it
                }
        }
        job.await()
        return discoveredDevice
    }

    private suspend fun onSuccessfulConnect(device: BluetoothDevice) {
        val deviceEntity = deviceRepository.getByAddress(device.address).first()
        deviceEntity?.let {
            onConnected(deviceEntity)
        }
    }

    fun getPairedDevices(): Set<BluetoothDevice> {
        return bluetoothAdapter.bondedDevices
    }

    private suspend fun onConnected(device: DeviceEntity) {
        device._actualConnectionState.value = BluetoothConnectionState.CONNECTED
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
        if (device._actualConnectionState.value != BluetoothConnectionState.RECONNECTING) {
            device._actualConnectionState.value = BluetoothConnectionState.CONNECTING
            saveDevice(device)
        }
    }

    private suspend fun onConnecting(macAddress: String) {
        val device = deviceRepository.getByAddress(macAddress).firstOrNull()
        device?.let {
            onConnecting(it)
        }
    }

    private suspend fun onReconnecting(device: DeviceEntity) {
        if (device._actualConnectionState.value != BluetoothConnectionState.CONNECTING) {
            device._actualConnectionState.value = BluetoothConnectionState.RECONNECTING
            saveDevice(device)
        }
    }

    private suspend fun onReconnecting(macAddress: String) {
        val device = deviceRepository.getByAddress(macAddress).firstOrNull()
        device?.let {
            onReconnecting(it)
        }
    }

    private suspend fun onDisconnected(device: DeviceEntity) {
//        device._actualConnectionState.value = BluetoothConnectionState.DISCONNECTED
//        bluetoothConnectionState.value = BluetoothConnectionState.DISCONNECTED

        saveDevice(device)
        EventBus.getDefault().post(BluetoothDeviceDisconnectedEvent(device))
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
    fun onSensorValueReceivedEvent(event: SensorValueReceivedEvent) {
        event.sensorValue?.let {
            it.location = locationService.getCurrentLocation()
            it.sequenceNumber = trackingManager.messageNumber.getAndIncrement()
            EventBus.getDefault().post(SendSensorValueMqttEvent(it))
        }
    }

    @Subscribe
    fun onStartBleScannerEvent(event: StartBleScannerEvent) {
        bluetoothAdapter.bluetoothLeScanner.startScan(event.leScanCallback)
    }

    @Subscribe
    fun onStopBleScannerEvent(event: StopBleScannerEvent) {
        bluetoothAdapter.bluetoothLeScanner.stopScan(event.leScanCallback)
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