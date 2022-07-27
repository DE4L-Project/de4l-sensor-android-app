package io.de4l.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.bluetooth.BluetoothScanner
import io.de4l.app.device.DeviceRepository
import io.de4l.app.tracking.BackgroundServiceWatcher
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val bluetoothScanner: BluetoothScanner,
    private val backgroundServiceWatcher: BackgroundServiceWatcher,
    private val deviceRepository: DeviceRepository,
    application: Application
) : AndroidViewModel(application) {

    private val LOG_TAG: String = DebugViewModel::class.java.name

    val activeScanJobs = bluetoothScanner.activeScanJobs
    val bluetoothScanScanState = bluetoothScanner.bluetoothScanState().asLiveData()


}