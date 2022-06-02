package io.de4l.app.bluetooth.event

import io.de4l.app.bluetooth.BluetoothDeviceType

class ConnectToBluetoothDeviceEvent(
    val macAddress: String,
    val deviceType: BluetoothDeviceType,
    val connectWithRetry: Boolean = true
) {

}
