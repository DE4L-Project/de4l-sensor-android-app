package io.de4l.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.R
import io.de4l.app.device.DeviceEntity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge

@AndroidEntryPoint
abstract class SensorValueFragment(private val deviceEntity: DeviceEntity?) : Fragment() {
    private val LOG_TAG = SensorValueFragment::class.java.name

    protected val viewModel: SensorValueViewModel by viewModels()

    lateinit var tvDeviceAddress: TextView
    lateinit var tvConnectionState: TextView


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvDeviceAddress = view.findViewById(R.id.tvDeviceAddress)
        tvConnectionState = view.findViewById(R.id.tvConnectionState)

        viewModel.selectedDevice.value = deviceEntity

        viewModel.selectedDevice.value?.let { selectedDevice ->
            selectedDevice._macAddress.asLiveData().observe(viewLifecycleOwner) {
                tvDeviceAddress.text = it
            }

            merge(
                selectedDevice._actualConnectionState,
                selectedDevice._targetConnectionState
            ).asLiveData().observe(viewLifecycleOwner) {
                tvConnectionState.text =
                    "Actual: ${selectedDevice._actualConnectionState.value} --> Target: ${selectedDevice._targetConnectionState.value}"
            }
        }


        viewModel.selectedDevice.filterNotNull().asLiveData().observe(viewLifecycleOwner) {


        }
    }

    protected open fun clearUi() {
        tvDeviceAddress.text = "-"
        tvConnectionState.text = "-"
    }
}