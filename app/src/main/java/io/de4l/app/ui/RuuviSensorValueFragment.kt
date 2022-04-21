package io.de4l.app.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.asLiveData
import io.de4l.app.R
import io.de4l.app.device.DeviceEntity
import io.de4l.app.sensor.SensorType
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

class RuuviSensorValueFragment(deviceEntity: DeviceEntity?) : SensorValueFragment(deviceEntity) {
    private val LOG_TAG = SensorValueFragment::class.java.name

    lateinit var tvTemperature: TextView
    lateinit var tvHumidity: TextView
    lateinit var tvPressure: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_ruuvi_values, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvTemperature = view.findViewById(R.id.tvTemperatureValue)
        tvHumidity = view.findViewById(R.id.tvHumidityValue)
        tvPressure = view.findViewById(R.id.tvPressureValue)

        viewModel.getFlowForProperty(SensorType.TEMPERATURE).filterNotNull().asLiveData()
            .observe(viewLifecycleOwner) {
                tvTemperature.text = String.format("%.2f °C", it.value)
            }

        viewModel.getFlowForProperty(SensorType.HUMIDITY).filterNotNull().asLiveData()
            .observe(viewLifecycleOwner) {
                tvHumidity.text = String.format("%.2f  %%", it.value)
            }

        viewModel.getFlowForProperty(SensorType.PRESSURE).filterNotNull().asLiveData()
            .observe(viewLifecycleOwner) {
                tvPressure.text = String.format("%.0f hPa", it.value)
            }


        viewModel.registerUiUpdates()
    }

    override fun clearUi() {
        clearSensorDataUi()
    }

    override fun clearSensorDataUi() {
        tvTemperature.text = "-"
        tvHumidity.text = "-"
        tvPressure.text = "-"
    }
}