package io.de4l.app.bluetooth

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.de4l.app.bluetooth.event.BluetoothDataReceivedEvent
import io.de4l.app.bluetooth.event.BluetoothDeviceConnectedEvent
import io.de4l.app.bluetooth.event.BluetoothDeviceDisconnectedEvent
import io.de4l.app.device.DeviceEntity
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.LocationService
import io.de4l.app.sensor.SensorValueParser
import io.de4l.app.ui.event.SensorValueReceivedEvent
import io.de4l.app.util.RetryException
import io.de4l.app.util.RetryHelper.Companion.runWithRetry
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.joda.time.DateTime
import javax.inject.Inject

class BluetoothDeviceManager @Inject constructor(
    val application: Application,
    val bluetoothAdapter: BluetoothAdapter,
    val locationService: LocationService,
    val deviceRepository: DeviceRepository,
    val sensorValueParser: SensorValueParser

) {
    private val LOG_TAG: String = BluetoothDeviceManager::class.java.name

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var bluetoothConnectionJob: Job? = null
    private var bluetoothSocketConnection: BluetoothSocketConnection? = null

    val bluetoothScanScanState = MutableStateFlow(BluetoothScanState.NOT_SCANNING)
    val bluetoothConnectionState = MutableStateFlow(BluetoothConnectionState.DISCONNECTED)

    init {
        EventBus.getDefault().register(this)
        coroutineScope.launch {
            bluetoothConnectionState.collect {
                Log.v(LOG_TAG, "CONNECTION STATE: " + bluetoothConnectionState.value)
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
            bluetoothConnectionState.value = BluetoothConnectionState.DISCONNECTED
        }
    }


    suspend fun connect(macAddress: String): Boolean {
        onConnecting(macAddress)
        val bluetoothDevice = findBtDevice(macAddress)
        bluetoothDevice?.let {
            connectToBtDevice(it)
            return true
        }
        onDisconnected(macAddress)
        return false
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
                        device?.let { offer(device) }
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
        closeBtConnection()
        bluetoothConnectionJob?.cancel()
        bluetoothConnectionJob = null
        coroutineScope.launch {
            onDisconnected(device)
        }
    }

    fun disconnect(device: BluetoothDevice) {
        coroutineScope.launch {
            val deviceEntity =
                deviceRepository.getByAddress(device.address).filterNotNull().first()
            disconnect(deviceEntity)
        }
    }

    fun forceReconnect() {
        bluetoothSocketConnection?.simulateConnectionLoss()
    }

    private suspend fun connectWithRetry(macAddress: String) {
        onConnecting(macAddress)

        val bluetoothDevice = findBtDeviceWithRetry(macAddress)
        bluetoothDevice?.let {
            runWithRetry { connectToBtDevice(it) }
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


    private suspend fun connectToBtDevice(
        device: BluetoothDevice,
    ) {
        closeBtConnection()
        bluetoothConnectionJob = coroutineScope.launch(Dispatchers.IO) {
            bluetoothSocketConnection = BluetoothSocketConnection(device)

            try {
                bluetoothSocketConnection?.connect {
                    coroutineScope.launch {
                        onSuccessfulConnect(device)
                    }
                }
                disconnect(device)
            } catch (e: BluetoothConnectionLostException) {
                Log.e(LOG_TAG, "Connection lost: ${e.message}")
                closeBtConnection()
                onReconnecting(device.address)
                connectWithRetry(device.address)
            }
        }
    }

    private suspend fun onSuccessfulConnect(device: BluetoothDevice) {
        val deviceEntity = deviceRepository.getByAddress(device.address).first()
        deviceEntity?.let {
            onConnected(deviceEntity)
        }
    }


    private fun closeBtConnection() {
        bluetoothSocketConnection?.closeConnection()
        bluetoothSocketConnection = null
    }

    fun getPairedDevices(): Set<BluetoothDevice> {
        return bluetoothAdapter.bondedDevices
    }

    private suspend fun onConnected(device: DeviceEntity) {
        device.connectionState = BluetoothConnectionState.CONNECTED
        bluetoothConnectionState.value = BluetoothConnectionState.CONNECTED
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
        if (bluetoothConnectionState.value != BluetoothConnectionState.RECONNECTING) {
            bluetoothConnectionState.value = BluetoothConnectionState.CONNECTING
        }

        if (device.connectionState != BluetoothConnectionState.RECONNECTING) {
            device.connectionState = BluetoothConnectionState.CONNECTING
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
        if (bluetoothConnectionState.value != BluetoothConnectionState.CONNECTING) {
            bluetoothConnectionState.value = BluetoothConnectionState.RECONNECTING
        }

        if (device.connectionState != BluetoothConnectionState.CONNECTING) {
            device.connectionState = BluetoothConnectionState.RECONNECTING
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
        device.connectionState = BluetoothConnectionState.DISCONNECTED
        bluetoothConnectionState.value = BluetoothConnectionState.DISCONNECTED
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
        deviceRepository.saveDevice(device)
    }

    @Subscribe
    fun onBluetoothDataReceivedEvent(event: BluetoothDataReceivedEvent) {
        val sensorValue =
            sensorValueParser.parseLine(
                event.device.address,
                event.data,
                locationService.getCurrentLocation(),
                DateTime()
            )
        EventBus.getDefault().post(SensorValueReceivedEvent(sensorValue))
    }

}