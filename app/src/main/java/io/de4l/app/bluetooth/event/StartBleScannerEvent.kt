package io.de4l.app.bluetooth.event

import android.bluetooth.le.ScanCallback

class StartBleScannerEvent(val leScanCallback: ScanCallback, val macAddress: String) {

}
