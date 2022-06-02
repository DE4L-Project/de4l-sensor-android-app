package io.de4l.app.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.de4l.app.BuildConfig
import io.de4l.app.util.LoggingHelper
import io.de4l.app.util.ObservableMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.pow

@SuppressLint("MissingPermission")
class BluetoothScanner @Inject constructor(
    val application: Application,
    val bluetoothAdapter: BluetoothAdapter,
) {
    private val LOG_TAG: String = BluetoothScanner::class.java.name

    //Scan Retry Params
    private val MAX_SCAN_RETRIES: Int = (Int.MAX_VALUE - 1)
    private val MAX_DELAY_MILLISECONDS: Long = 60000
    private val BACKOFF_MULTIPLIER = 2.0
    private val INITIAL_DELAY: Long = 1000
    private var retryCounter = 0

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val bluetoothScanScanState: MutableStateFlow<BluetoothScanState> =
        MutableStateFlow(BluetoothScanState.NOT_SCANNING);

    private val retryFlow: MutableSharedFlow<Boolean> = MutableSharedFlow()
    private val discoveredDevices: MutableSharedFlow<BluetoothDevice> = MutableSharedFlow()

    val activeScanJobs: ObservableMap<String, BluetoothScanJob> = ObservableMap(mutableMapOf())
    private var currentDeviceScanTask: Job? = null
    private var currentBleScanCallback: ScanCallback? = null

    private val cancelDeviceDiscoveryChannel: MutableSharedFlow<Boolean> = MutableSharedFlow()

    init {
        coroutineScope.launch { registerScanJobListener() }

        if (BuildConfig.DEBUG) {
            coroutineScope.launch {
                while (true) {
                    LoggingHelper.logWithCurrentThread(
                        LOG_TAG,
                        "Scanning: ${bluetoothAdapter.isDiscovering}"
                    )
                    delay(1000)
                }
            }
        }
    }

    suspend fun findBtDevice(
        macAddress: String,
        deviceType: BluetoothDeviceType,
        findWithRetry: Boolean = false
    ): BluetoothDevice? {
        LoggingHelper.logWithCurrentThread(LOG_TAG, "findBtDevice: ${macAddress}")
        val scanJob = BluetoothScanJob(macAddress, deviceType, findWithRetry)
        activeScanJobs[macAddress] = scanJob;
        return scanJob.waitForDevice()
    }


    fun stopSearchForDevice(macAddress: String?) {
        LoggingHelper.logWithCurrentThread(LOG_TAG, "stopSearchForDevice: ${macAddress}")
        macAddress?.let {
            activeScanJobs.remove(macAddress)
        }
    }

    fun bluetoothScanState(): StateFlow<BluetoothScanState> {
        return bluetoothScanScanState.asStateFlow();
    }

//    private suspend fun startScanning() {
//        bluetoothScanScanState.value = BluetoothScanState.PENDING
//        scanForDevices()
//            .catch {
//                retryCounter++
//                cancelAllNonRetrieableScanJobs()
//                retryFlow.emit(true)
//            }
//            .collect {
//                discoveredDevices.emit(it)
//            }
//    }

    private suspend fun registerScanJobListener() {
        merge(
            activeScanJobs.changed(),
            retryFlow
        )
            .filter { it }
            .flowOn(Dispatchers.IO)
            .collect {
                LoggingHelper.logWithCurrentThread(LOG_TAG, "scanJobs changed")
                if (activeScanJobs.isNotEmpty() && bluetoothScanScanState.value === BluetoothScanState.NOT_SCANNING) {
                    bluetoothScanScanState.value = BluetoothScanState.PENDING
                    LoggingHelper.logWithCurrentThread(LOG_TAG, "Start Scan - ${retryCounter}")
                    currentDeviceScanTask = coroutineScope.launch {
                        calculateDelay()
                        val useLegacyMode =
                            activeScanJobs.values().any { it.isLegacyScanJob() }

                        scanForDevices(useLegacyMode)
                            .catch {
                                if (it is BluetoothScanFinished) {
                                    retryCounter++
                                    cancelAllNonRetrieableScanJobs()
                                    if (retryCounter < MAX_SCAN_RETRIES) {
                                        retryFlow.emit(true)
                                    } else {
                                        cancelAllScanJobs()
                                    }
                                }
                            }
                            .collect {
                                onDeviceFound(it)
                            }
                    }
                    LoggingHelper.logWithCurrentThread(LOG_TAG, "After scan for Devices")
                }

                coroutineScope.launch {
                    if (activeScanJobs.isEmpty()) {
                        LoggingHelper.logWithCurrentThread(LOG_TAG, "Scan Jobs is empty")
                        cancelDeviceDiscovery()
                    }
                }
            }
    }

    private suspend fun calculateDelay() {
        val delayMs =
            if (retryCounter == 0)
                0
            else
                (INITIAL_DELAY * BACKOFF_MULTIPLIER.pow(retryCounter.toDouble()).toLong())
                    .coerceAtMost(MAX_DELAY_MILLISECONDS)

        LoggingHelper.logWithCurrentThread(LOG_TAG, "Delay for ${delayMs} ms")
        delay(delayMs)
    }

    private suspend fun onDeviceFound(bluetoothDevice: BluetoothDevice) {
        discoveredDevices.emit(bluetoothDevice)

        //Check for scan jobs
        val scanJob = activeScanJobs[bluetoothDevice.address]
        scanJob?.let {
            activeScanJobs.remove(bluetoothDevice.address)
            it.onSuccess(bluetoothDevice)
            LoggingHelper.logWithCurrentThread(
                LOG_TAG,
                "Remove ScanJob - ${bluetoothDevice.address}"
            )
        }
    }

    private suspend fun scanForLegacyDevices() = callbackFlow<BluetoothDevice> {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                coroutineScope.launch {
                    when (intent?.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            LoggingHelper.logWithCurrentThread(LOG_TAG, "ACTION_FOUND")
                            val device =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                            device?.let {
                                try {
                                    coroutineScope.launch {
                                        send(device)
                                    }
                                } catch (e: ClosedSendChannelException) {
                                    LoggingHelper.logWithCurrentThread(
                                        LOG_TAG,
                                        "Closed Channel Exception in BluetoothDeviceManager:onReceive - Can be ignored."
                                    )
                                }
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                            LoggingHelper.logWithCurrentThread(LOG_TAG, "ACTION_DISCOVERY_STARTED")
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            LoggingHelper.logWithCurrentThread(LOG_TAG, "ACTION_DISCOVERY_FINISHED")
                            close(BluetoothScanFinished())
                        }
                    }
                }
            }
        }
        application.registerReceiver(receiver, filter)

        startDeviceDiscovery()

        awaitClose {
            bluetoothAdapter.cancelDiscovery()
            application.unregisterReceiver(receiver)
        }
    }

    private suspend fun scanForBleDevice() =
        callbackFlow<BluetoothDevice> {
            val scanFilter: ScanFilter = ScanFilter.Builder().build()
            val scanSettings: ScanSettings =
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                    .build()

            currentBleScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result?.let {
                        if (result.device.name != null && result.device.name.startsWith("Ruuvi")) {
                            LoggingHelper.logWithCurrentThread(
                                LOG_TAG,
                                "RUUVI_TAG: " + result.device.address
                            )
                        }
                        coroutineScope.launch {
                            trySend(result.device)
                        }
                    }
                }
            }

            bluetoothAdapter.bluetoothLeScanner.startScan(
                listOf(scanFilter),
                scanSettings,
                currentBleScanCallback
            )

            awaitClose {
                bluetoothAdapter.bluetoothLeScanner.stopScan(currentBleScanCallback)
            }
        }

    //https://github.com/ThanosFisherman/BlueFlow
    @ExperimentalCoroutinesApi
    suspend fun scanForDevices(useLegacyMode: Boolean = false) = callbackFlow<BluetoothDevice> {
        LoggingHelper.logWithCurrentThread(LOG_TAG, "scanForDevices()")

        var bleScanJob: Job? = null;
        var legacyScanJob: Job? = null;
        var legacyScanJobTimeout: Job? = null;
        var cancelDeviceDiscoveryJob: Job? = null;

        if (bluetoothScanScanState.value == BluetoothScanState.SCANNING) {
            Log.w(LOG_TAG, "Is already scanning.")
            close(BluetoothScanFinished())
        }

        bluetoothScanScanState.value = BluetoothScanState.SCANNING

        val bleScanJobTimeout = launch {
            delay(20000)
            LoggingHelper.logWithCurrentThread(LOG_TAG, "BLE Scan Timeout")
            bleScanJob?.cancel()

            //If legacy mode is needed, try again
            if (useLegacyMode) {
                legacyScanJobTimeout = launch {
                    delay(20000)
                    LoggingHelper.logWithCurrentThread(LOG_TAG, "LEGACY Scan Timeout")
                    legacyScanJob?.cancel()
                    close(BluetoothScanFinished())
                }

                legacyScanJob = coroutineScope.launch {
                    scanForLegacyDevices()
                        .catch {
                            //Not important
                            LoggingHelper.logWithCurrentThread(
                                LOG_TAG,
                                "Discovering legacy devices finished"
                            )
                        }
                        .collect {
                            send(it)
                        }
                }
            } else {
                close(BluetoothScanFinished())
            }
        }

        cancelDeviceDiscoveryJob = coroutineScope.launch {
            cancelDeviceDiscoveryChannel.filter { it }
                .collect {
                    close(BluetoothScanFinished())
                }
        }

        bleScanJob = coroutineScope.launch {
            scanForBleDevice()
                .catch {
                    //Not important
                    LoggingHelper.logWithCurrentThread(
                        LOG_TAG,
                        "Discovering BLE devices finished"
                    )
                }
                .collect {
                    send(it)
                }
        }

        awaitClose {
            LoggingHelper.logWithCurrentThread(LOG_TAG, "Await Close")
            bleScanJob.cancel()
            bleScanJobTimeout.cancel()
            legacyScanJob?.cancel()
            legacyScanJobTimeout?.cancel()
            cancelDeviceDiscoveryJob.cancel()
            bluetoothScanScanState.value = BluetoothScanState.NOT_SCANNING
        }
    }
        .filterNotNull()
        .flowOn(Dispatchers.IO)

    private class BluetoothScanFinished : Throwable() {

    }

    fun startDeviceDiscovery() {
        bluetoothAdapter.startDiscovery()
    }

    fun stopDeviceDiscovery() {
        coroutineScope.launch {
            cancelDeviceDiscoveryChannel.emit(true)
        }
    }

    fun cancelDeviceDiscovery() {
        LoggingHelper.logWithCurrentThread(LOG_TAG, "Cancel discovery")
        currentDeviceScanTask?.cancel()
        currentDeviceScanTask = null
        stopDeviceDiscovery()
//        bluetoothAdapter.cancelDiscovery()
//        bluetoothAdapter.bluetoothLeScanner.stopScan(currentBleScanCallback)
//        bluetoothScanScanState.value = BluetoothScanState.NOT_SCANNING
        retryCounter = 0;
    }

    suspend fun discoverDevices(useLegacyMode: Boolean = false): SharedFlow<BluetoothDevice> {
        LoggingHelper.logWithCurrentThread(LOG_TAG, "discoverDevices()")
        if (bluetoothScanScanState.value !== BluetoothScanState.SCANNING) {
            bluetoothScanScanState.value = BluetoothScanState.PENDING
            coroutineScope.launch {
                scanForDevices(useLegacyMode)
                    .catch {
                        //Not important
                        LoggingHelper.logWithCurrentThread(
                            LOG_TAG,
                            "Discovering devices finished"
                        )
                    }
                    .onCompletion {
                        //Check for retries
                        retryFlow.emit(true)
                    }
                    .collect { onDeviceFound(it) }
            }
        }
        return discoveredDevices.asSharedFlow()
    }


    private suspend fun cancelAllNonRetrieableScanJobs() {
        val nonRetrieableScanJobKeys = activeScanJobs.toMap().filterValues { !it.retry }.keys
        activeScanJobs.toMap().filterKeys { nonRetrieableScanJobKeys.contains(it) }.values.forEach {
            it.onError()
        }
        activeScanJobs.removeAllKeys(nonRetrieableScanJobKeys)

    }

    private suspend fun cancelAllScanJobs() {
        activeScanJobs.toMap().values.forEach {
            it.onError()
        }
        activeScanJobs.clear()
    }

    fun startBleScan(leScanCallback: ScanCallback, macAddress: String) {
        val scanSettings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();

        val scanFilter = ScanFilter.Builder().setDeviceAddress(macAddress).build()

        bluetoothAdapter.bluetoothLeScanner.startScan(
            listOf(scanFilter),
            scanSettings,
            leScanCallback
        )
    }

    fun stopBleScan(leScanCallback: ScanCallback) {
        bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
    }
}