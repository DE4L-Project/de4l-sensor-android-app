package io.de4l.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.asLiveData
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.R

@AndroidEntryPoint
class DebugFragment : Fragment() {
    private val LOG_TAG = DebugFragment::class.java.name

    private lateinit var tvScanJobQueue: TextView;
    private lateinit var tvBluetoothScanState: TextView;

    private val viewModel: DebugViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_debug, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvScanJobQueue = view.findViewById(R.id.tvBluetoothDeviceManagerScanJobQueue)
        tvBluetoothScanState = view.findViewById(R.id.tvBluetoothScanState)

        viewModel.activeScanJobs.changed().asLiveData().observe(viewLifecycleOwner) {
            var macAddressesAsString = "";
            viewModel.activeScanJobs.toMap().forEach {
                macAddressesAsString += "${it.key}, "
            }
            tvScanJobQueue.text = macAddressesAsString
        }

        viewModel.bluetoothScanScanState.observe(viewLifecycleOwner) {
            tvBluetoothScanState.text = it.toString()
        }


    }
}