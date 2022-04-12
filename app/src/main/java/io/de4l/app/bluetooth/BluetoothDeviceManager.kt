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
import com.google.common.collect.ImmutableSet
import io.de4l.app.bluetooth.event.BluetoothDeviceConnectedEvent
import io.de4l.app.bluetooth.event.BluetoothDeviceDisconnectedEvent
import io.de4l.app.bluetooth.event.ConnectToBluetoothDeviceEvent
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.device.StopBleScannerEvent
import io.de4l.app.location.LocationService
import io.de4l.app.sensor.RuuviTagParser
import io.de4l.app.tracking.BackgroundServiceWatcher
import io.de4l.app.tracking.TrackingManager
import io.de4l.app.ui.event.SendSensorValueMqttEvent
import io.de4l.app.ui.event.SensorValueReceivedEvent
import io.de4l.app.util.LoggingHelper
import io.de4l.app.util.ObservableMap
import io.de4l.app.util.ObservableSet
import io.de4l.app.util.RetryException
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject


@SuppressLint("MissingPermission")
class BluetoothDeviceManager @Inject constructor(
    val application: Application,
    val bluetoothAdapter: BluetoothAdapter,
    val locationService: LocationService,
    val deviceRepository: DeviceRepository,
    val trackingManager: TrackingManager,
    val backgroundServiceWatcher: BackgroundServiceWatcher
) {
    private val LOG_TAG: String = BluetoothDeviceManager::class.java.name
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
//    private var leScanCallback: ScanCallback? = null

    private val scanJobQueue: MutableSharedFlow<BluetoothScanJob> = MutableSharedFlow()
    private val _wantedDevices: ObservableSet<String> = ObservableSet(mutableSetOf())

    private val _scanJobs: ObservableMap<String, BluetoothScanJob> = ObservableMap(HashMap());

    private val _scannedDevices: MutableSharedFlow<BluetoothDevice> = MutableSharedFlow()

    private val bluetoothScanScanState = MutableStateFlow(BluetoothScanState.NOT_SCANNING)

    init {
        EventBus.getDefault().register(this)

        coroutineScope.launch {
            val connectedDevices = deviceRepository.getDevicesShouldBeConnected().firstOrNull()
            connectedDevices?.forEach { device ->
                device._macAddress.value?.let { macAddress ->
                    backgroundServiceWatcher.sendEventToService(
                        ConnectToBluetoothDeviceEvent(
                            macAddress
                        )
                    )
                }
            }
        }

        coroutineScope.launch {
            scanJobQueue.filterNotNull().collect {
                _wantedDevices.add(it.macAddress)

                if (!_scanJobs.containsKey(it.macAddress)) {
                    _scanJobs[it.macAddress] = it

                    //Start job only if not already scanning
                    if (bluetoothScanScanState.value == BluetoothScanState.NOT_SCANNING) {
                        bluetoothScanScanState.value = BluetoothScanState.PENDING
                        launch {
                            scanForDevices()
                                .onCompletion {
                                    Log.v(LOG_TAG, "Scan for devices finished")
                                    val scanJobs = _scanJobs.toMap()
                                    _scanJobs.clear()
                                    scanJobs.values.forEach { scanJob ->
                                        scanJob.onError()
                                    }
                                }
                                .collect()
                        }
                    }
                }
            }
        }

        coroutineScope.launch {
            _scannedDevices.collect { bluetoothDevice ->
                if (_scanJobs.isNotEmpty()) {
                    val scanJob = _scanJobs[bluetoothDevice.address]
                    scanJob?.onSuccess(bluetoothDevice)
                    _scanJobs.remove(bluetoothDevice.address)
                    _wantedDevices.remove(bluetoothDevice.address)
                    if (_scanJobs.isEmpty()) {
                        cancelDeviceDiscovery()
                    }
                }
            }
        }


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
        onConnecting(macAddress)
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
    }

    //https://github.com/ThanosFisherman/BlueFlow
    @ExperimentalCoroutinesApi
    suspend fun scanForDevices() = callbackFlow<BluetoothDevice> {
        if (bluetoothScanScanState.value == BluetoothScanState.SCANNING) {
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
                        Log.v(LOG_TAG, "ACTION_FOUND")
                        val device =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                        device?.let {
                            try {
                                coroutineScope.launch {
                                    _scannedDevices.emit(it)
                                }
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
            device._macAddress.value?.let { _scanJobs.remove(it) }
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


    private suspend fun findBtDeviceWithRetry(macAddress: String): BluetoothDevice? {
        var discoveredDevice: BluetoothDevice? = null
        runWithRetry {
            try {
                discoveredDevice = findBtDevice(macAddress)
                //Only retry if user did not stop search
                if (_wantedDevices.contains(macAddress) && discoveredDevice == null) {
                    throw RetryException("No device found; ${macAddress}")
                }
            } catch (e: BluetoothAlreadyScanningException) {
//                cancelDeviceDiscovery()
//                throw RetryException("Bluetooth Already Scanning")
            }

        }
        return discoveredDevice
    }

    @ExperimentalCoroutinesApi
    private suspend fun findBtDevice(macAddress: String): BluetoothDevice? {
        Log.v(LOG_TAG, "findBtDevice: ${macAddress}")

        var discoveredDevice: BluetoothDevice? = null
        val bluetoothScanJob = BluetoothScanJob(macAddress)
        scanJobQueue.emit(bluetoothScanJob)
        discoveredDevice = bluetoothScanJob.waitForDevice()
        Log.v(LOG_TAG, "Discovered Device: ${macAddress} - ${discoveredDevice?.address}")

//        val job = coroutineScope.async {
//            scanForDevices()
//                .filter {
//                    it.address == macAddress
//                }
//                .collect {
//                    cancelDeviceDiscovery()
//                    Log.i(LOG_TAG, "Device: ${it.address}")
//                    discoveredDevice = it
//                }
//        }
//        job.await()
        return discoveredDevice
    }

    fun getPairedDevices(): Set<BluetoothDevice> {
        return bluetoothAdapter.bondedDevices
    }

    private suspend fun onConnected(device: DeviceEntity) {
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - CONNECTED")
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
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - CONNECTING")
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
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - RECONNECTING")
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
        Log.v(LOG_TAG, "Device ${device._macAddress.value} - DISCONNECTED")
        saveDevice(device)

        if (device._targetConnectionState.value == BluetoothConnectionState.DISCONNECTED) {
            device._macAddress.value?.let { _wantedDevices.remove(it) }
        }

        if (device._targetConnectionState.value == BluetoothConnectionState.CONNECTED) {
            onReconnecting(device)
            device._macAddress.value?.let {
                connectDeviceWithRetry(it)
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

    fun scanJobs(): ObservableMap<String, BluetoothScanJob> {
        return _scanJobs;
    }


    fun bluetoothScanState(): StateFlow<BluetoothScanState> {
        return bluetoothScanScanState;
    }

    fun wantedDevices(): ObservableSet<String> {
        return _wantedDevices
    }

    @Subscribe
    fun onBtDeviceConnectionChange(event: BtDeviceConnectionChangeEvent) {
        coroutineScope.launch {
            val deviceEntity = event.deviceEntity

            deviceEntity._actualConnectionState.asSharedFlow()
                .collect { connectionState ->
                    when (connectionState) {
                        BluetoothConnectionState.CONNECTED -> onConnected(deviceEntity)
                        BluetoothConnectionState.CONNECTING -> onConnecting(deviceEntity)
                        BluetoothConnectionState.RECONNECTING -> onReconnecting(deviceEntity)
                        BluetoothConnectionState.DISCONNECTED -> onDisconnected(deviceEntity)
                    }
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

    @Subscribe
    fun onStartBleScannerEvent(event: StartBleScannerEvent) {
        LoggingHelper.logCurrentThread(LOG_TAG, "onStartBleScannerEvent()")
        bluetoothAdapter.bluetoothLeScanner.startScan(event.leScanCallback)
        Log.v(LOG_TAG, "Is Bluetooth Adapter Discovering: ${bluetoothAdapter.isDiscovering()}")
    }

    @Subscribe
    fun onStopBleScannerEvent(event: StopBleScannerEvent) {
        LoggingHelper.logCurrentThread(LOG_TAG, "onStopBleScannerEvent()")
        bluetoothAdapter.bluetoothLeScanner.stopScan(event.leScanCallback)
        Log.v(LOG_TAG, "Is Bluetooth Adapter Discovering: ${bluetoothAdapter.isDiscovering()}")

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