package io.de4l.app.bluetooth

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import io.de4l.app.bluetooth.event.StartBleScannerEvent
import io.de4l.app.bluetooth.event.StopBleScannerEvent
import io.de4l.app.util.LoggingHelper
import io.de4l.app.util.ObservableMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
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

    init {
        EventBus.getDefault().register(this)
        coroutineScope.launch { registerScanJobListener() }
        coroutineScope.launch {
            while (true) {
                LoggingHelper.logCurrentThread(
                    LOG_TAG,
                    "Scanning: ${bluetoothAdapter.isDiscovering}"
                )
                delay(1000)
            }
        }
    }

    suspend fun findBtDevice(macAddress: String, findWithRetry: Boolean = false): BluetoothDevice? {
        LoggingHelper.logCurrentThread(LOG_TAG, "findBtDevice: ${macAddress}")
        val scanJob = BluetoothScanJob(macAddress, findWithRetry)
        activeScanJobs[macAddress] = scanJob;
        return scanJob.waitForDevice()
    }


    fun stopSearchForDevice(macAddress: String?) {
        LoggingHelper.logCurrentThread(LOG_TAG, "stopSearchForDevice: ${macAddress}")
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
                LoggingHelper.logCurrentThread(LOG_TAG, "scanJobs changed")
                if (activeScanJobs.isNotEmpty() && bluetoothScanScanState.value === BluetoothScanState.NOT_SCANNING) {
                    bluetoothScanScanState.value = BluetoothScanState.PENDING
                    LoggingHelper.logCurrentThread(LOG_TAG, "Start Scan - ${retryCounter}")
                    currentDeviceScanTask = coroutineScope.launch {
                        calculateDelay()
                        scanForDevices()
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
                    LoggingHelper.logCurrentThread(LOG_TAG, "After scan for Devices")
                }

                coroutineScope.launch {
                    if (activeScanJobs.isEmpty()) {
                        LoggingHelper.logCurrentThread(LOG_TAG, "Scan Jobs is empty")
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

        LoggingHelper.logCurrentThread(LOG_TAG, "Delay for ${delayMs} ms")
        delay(delayMs)
    }

    private suspend fun onDeviceFound(bluetoothDevice: BluetoothDevice) {
        discoveredDevices.emit(bluetoothDevice)

        //Check for scan jobs
        val scanJob = activeScanJobs[bluetoothDevice.address]
        scanJob?.let {
            activeScanJobs.remove(bluetoothDevice.address)
            it.onSuccess(bluetoothDevice)
            LoggingHelper.logCurrentThread(LOG_TAG, "Remove ScanJob - ${bluetoothDevice.address}")
        }
    }

    //https://github.com/ThanosFisherman/BlueFlow
    @ExperimentalCoroutinesApi
    suspend fun scanForDevices() = callbackFlow<BluetoothDevice> {
        LoggingHelper.logCurrentThread(LOG_TAG, "scanForDevices()")
        if (bluetoothScanScanState.value == BluetoothScanState.SCANNING) {
            Log.w(LOG_TAG, "Is already scanning.")
            close(BluetoothScanFinished())
        }

        bluetoothScanScanState.value = BluetoothScanState.SCANNING

        val scanJobTimeout = launch {
            delay(20000)
            LoggingHelper.logCurrentThread(LOG_TAG, "Scan Timeout")
            close(BluetoothScanFinished())
        }

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
                            LoggingHelper.logCurrentThread(LOG_TAG, "ACTION_FOUND")
                            val device =
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice?
                            device?.let {
                                try {
                                    trySend(device)
                                } catch (e: ClosedSendChannelException) {
                                    LoggingHelper.logCurrentThread(
                                        LOG_TAG,
                                        "Closed Channel Exception in BluetoothDeviceManager:onReceive - Can be ignored."
                                    )
                                }
                            }
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                            LoggingHelper.logCurrentThread(LOG_TAG, "ACTION_DISCOVERY_STARTED")
                        }
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                            LoggingHelper.logCurrentThread(LOG_TAG, "ACTION_DISCOVERY_FINISHED")
                            close(BluetoothScanFinished())
                        }
                    }
                }
            }
        }

        application.registerReceiver(receiver, filter)
        startDeviceDiscovery()

        awaitClose {
            LoggingHelper.logCurrentThread(LOG_TAG, "Await Close")
            scanJobTimeout.cancel()
            bluetoothAdapter.cancelDiscovery()
            application.unregisterReceiver(receiver)
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


    fun cancelDeviceDiscovery() {
        LoggingHelper.logCurrentThread(LOG_TAG, "Cancel discovery")
        currentDeviceScanTask?.cancel()
        currentDeviceScanTask = null
        bluetoothAdapter.cancelDiscovery()
        bluetoothScanScanState.value = BluetoothScanState.NOT_SCANNING
        retryCounter = 0;
    }

    suspend fun discoverDevices(): SharedFlow<BluetoothDevice> {
        LoggingHelper.logCurrentThread(LOG_TAG, "discoverDevices()")
        if (bluetoothScanScanState.value !== BluetoothScanState.SCANNING) {
            bluetoothScanScanState.value = BluetoothScanState.PENDING
            coroutineScope.launch { scanForDevices().collect { onDeviceFound(it) } }
        }
        return discoveredDevices.asSharedFlow()
    }

    @Subscribe
    fun onStartBleScannerEvent(event: StartBleScannerEvent) {
        LoggingHelper.logCurrentThread(LOG_TAG, "onStartBleScannerEvent()")
        bluetoothAdapter.bluetoothLeScanner.startScan(event.leScanCallback)
    }

    @Subscribe
    fun onStopBleScannerEvent(event: StopBleScannerEvent) {
        LoggingHelper.logCurrentThread(LOG_TAG, "onStopBleScannerEvent()")
        bluetoothAdapter.bluetoothLeScanner.stopScan(event.leScanCallback)

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
}