package io.de4l.app.ui

import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.R
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.device.DeviceEntity
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.merge

@AndroidEntryPoint
abstract class SensorValueFragment(private val deviceEntity: DeviceEntity?) : Fragment() {
    private val LOG_TAG = SensorValueFragment::class.java.name

    protected val viewModel: SensorValueViewModel by viewModels()

    lateinit var tvDeviceName: TextView
    lateinit var tvDeviceAddress: TextView
    lateinit var tvConnectionState: TextView
    lateinit var btnDisconnectSensor: ImageButton


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvDeviceName = view.findViewById(R.id.tvDeviceName)
        tvDeviceAddress = view.findViewById(R.id.tvDeviceAddress)
        tvConnectionState = view.findViewById(R.id.tvConnectionState)
        btnDisconnectSensor = view.findViewById(R.id.btnDisconnectSensor)
        btnDisconnectSensor.setOnClickListener {
            viewModel.onDisconnectClicked()
        }

        viewModel.selectedDevice.value = deviceEntity

        viewModel.clearUiEventFlow.filter { it }.asLiveData()
            .observe(viewLifecycleOwner) {
                clearSensorDataUi()
            }

        viewModel.selectedDevice.value?.let { selectedDevice ->
            selectedDevice._macAddress.asLiveData().observe(viewLifecycleOwner) {
                tvDeviceAddress.text = it
            }

            selectedDevice._name.asLiveData().observe(viewLifecycleOwner) {
                tvDeviceName.text = it
            }

            merge(
                selectedDevice._actualConnectionState,
                selectedDevice._targetConnectionState
            ).asLiveData().observe(viewLifecycleOwner) {
                tvConnectionState.text =
                    "Actual: ${selectedDevice._actualConnectionState.value} --> Target: ${selectedDevice._targetConnectionState.value}"
            }

            selectedDevice._targetConnectionState.asLiveData().observe(viewLifecycleOwner) {
                if (it == BluetoothConnectionState.DISCONNECTED) {
                    btnDisconnectSensor.setImageDrawable(
                        resources.getDrawable(
                            R.drawable.ic_link_24,
                            requireContext().theme
                        )
                    )
                } else {
                    btnDisconnectSensor.setImageDrawable(
                        resources.getDrawable(
                            R.drawable.ic_unlink_24,
                            requireContext().theme
                        )
                    )
                }
            }
        }


    }

    protected open fun clearUi() {
        tvDeviceAddress.text = "-"
        tvConnectionState.text = "-"
    }

    abstract fun clearSensorDataUi()
}