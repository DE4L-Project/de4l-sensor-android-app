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

class AirBeamSensorValueFragment(deviceEntity: DeviceEntity?) : SensorValueFragment(deviceEntity) {
    private val LOG_TAG = SensorValueFragment::class.java.name

    lateinit var tvTemperature: TextView
    lateinit var tvHumidity: TextView
    lateinit var tvPm1: TextView
    lateinit var tvPm25: TextView
    lateinit var tvPm10: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_airbeam2_values, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tvTemperature = view.findViewById(R.id.tvTemperatureValue)
        tvHumidity = view.findViewById(R.id.tvHumidityValue)
        tvPm1 = view.findViewById(R.id.tvPm1Value)
        tvPm25 = view.findViewById(R.id.tvPm25Value)
        tvPm10 = view.findViewById(R.id.tvPm10Value)

        viewModel.selectedDevice.value?._sensorValues?.asLiveData()?.observe(viewLifecycleOwner) {
            Log.i(LOG_TAG, "Sensor Value received: ${it?.sensorId}")
            when (it?.sensorType) {
                SensorType.TEMPERATURE -> tvTemperature.text =
                    String.format("%.2f °C", it.value)
                SensorType.HUMIDITY -> tvHumidity.text =
                    String.format("%.2f  %%", it.value)
                SensorType.PM1 -> tvPm1.text = String.format("%.0f ppm", it.value)
                SensorType.PM2_5 -> tvPm25.text = String.format("%.0f ppm", it.value)
                SensorType.PM10 -> tvPm10.text = String.format("%.0f ppm", it.value)
                null -> clearUi()
            }
        }
    }

    override fun clearUi() {
        tvTemperature.text = "-"
        tvHumidity.text = "-"
        tvPm1.text = "-"
        tvPm25.text = "-"
        tvPm10.text = "-"
    }
}