package io.de4l.app.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.de4l.app.device.DeviceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class SensorValueViewModel @Inject constructor() : ViewModel() {
    private val LOG_TAG = SensorValueViewModel::class.java.name

    val selectedDevice: MutableStateFlow<DeviceEntity?> = MutableStateFlow(null)

}