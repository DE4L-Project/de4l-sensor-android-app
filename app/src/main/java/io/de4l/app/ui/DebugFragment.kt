package io.de4l.app.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.R

@AndroidEntryPoint
class DebugFragment : Fragment() {
    private val LOG_TAG = DebugFragment::class.java.name

    private lateinit var btnAirBeam3Test: Button;
    private lateinit var tvConnectionState: TextView;
    private lateinit var btnAirBeam3Status: Button;
    private lateinit var btnAirBeam2Status: Button;
    private lateinit var btnConnectionLoss: Button;

    private lateinit var tvDebugAb2Values: TextView;
    private lateinit var tvDebugAb3Values: TextView;

    private val viewModel: DebugViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvConnectionState = view.findViewById(R.id.tvDebugConnectionInfo)
        tvDebugAb2Values = view.findViewById(R.id.tvDebugAb2)
        tvDebugAb3Values = view.findViewById(R.id.tvDebugAb3)

//        btnAirBeam3Test = view.findViewById(R.id.btnAirBeam3TestDebug)
//        btnAirBeam3Test.setOnClickListener {
//            viewModel.onConnectToAirBeam3()
//        }

        btnAirBeam3Status = view.findViewById(R.id.btnAirBeam3Status)
        btnAirBeam3Status.setOnClickListener {
            Log.i(
                LOG_TAG,
                "BleDeviceTest - DebugFragment::btnAirBeam3Status - ${Thread.currentThread().name}"
            )
            viewModel.onConnectToAirBeam3()
        }

        btnAirBeam2Status = view.findViewById(R.id.btnAirbeam2Status)
        btnAirBeam2Status.setOnClickListener {
            viewModel.onConnectToAirBeam2()
        }

        btnConnectionLoss = view.findViewById(R.id.btnConnectionLoss)
        btnConnectionLoss.setOnClickListener {
            viewModel.onConnectionLoss()
        }

        viewModel._airbeam2.observe(viewLifecycleOwner) { deviceEntity ->
            deviceEntity?.let {
                deviceEntity._actualConnectionState.asLiveData().observe(viewLifecycleOwner) {
                    btnAirBeam2Status.text = "Airbeam2 - ${it.name}"
                }

                deviceEntity._sensorValues.asLiveData().observe(viewLifecycleOwner) {
                    tvDebugAb2Values.text = it?.rawData
                }
            }
        }

        viewModel._airbeam3.observe(viewLifecycleOwner) { deviceEntity ->
            deviceEntity?.let {
                deviceEntity._actualConnectionState.asLiveData().observe(viewLifecycleOwner) {
                    btnAirBeam3Status.text = "Airbeam3 - ${it.name}"
                }

                deviceEntity._sensorValues.asLiveData().observe(viewLifecycleOwner) {
                    tvDebugAb3Values.text = it?.rawData
                }
            }
        }
    }
}