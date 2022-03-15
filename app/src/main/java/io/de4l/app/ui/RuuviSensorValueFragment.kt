package io.de4l.app.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.asLiveData
import io.de4l.app.R
import io.de4l.app.sensor.SensorType

class RuuviSensorValueFragment : SensorValueFragment() {
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

        viewModel.sensorValues.asLiveData().observe(viewLifecycleOwner) {
            Log.i(LOG_TAG, "Sensor Value received: ${it?.sensorId}")
            when (it?.sensorType) {
                SensorType.TEMPERATURE -> tvTemperature.text =
                    String.format("%.2f °C", it.value)
                SensorType.HUMIDITY -> tvHumidity.text =
                    String.format("%.2f  %%", it.value)
                SensorType.PRESSURE -> tvPressure.text = String.format("%.0f hPa", it.value)
                null -> clearUi()
            }
        }
    }

    override fun clearUi() {
        tvTemperature.text = "-"
        tvHumidity.text = "-"
        tvPressure.text = "-"
    }
}