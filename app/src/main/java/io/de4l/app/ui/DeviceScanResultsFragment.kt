package io.de4l.app.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hoc081098.flowext.throttleTime
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.R
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.BluetoothScanState.NOT_SCANNING
import io.de4l.app.bluetooth.BluetoothScanState.SCANNING
import io.de4l.app.device.DeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.system.measureTimeMillis

@AndroidEntryPoint
class DeviceScanResultsFragment : Fragment() {
    private val LOG_TAG = DeviceScanResultsFragment::class.java.name

    @Inject
    lateinit var bluetoothDeviceManager: BluetoothDeviceManager

    private val viewModel: DeviceScanResultsViewModel by viewModels()
    private val devices: MutableMap<String, DeviceEntity> = mutableMapOf()
    private val updateDeviceListEvents: MutableSharedFlow<Boolean> = MutableSharedFlow();


    private lateinit var rvBtDevices: RecyclerView
    private lateinit var tvScanHeader: TextView
    private lateinit var swLegacyBt: SwitchCompat

    override fun onResume() {
        super.onResume()
        viewModel.startScanning()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        devices.clear()
        rvBtDevices.adapter?.notifyDataSetChanged()
    }

    fun updateList() {
        updateDeviceListEvents.tryEmit(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel.viewModelScope.launch {
            updateDeviceListEvents
//                .filter { it }
                .throttleTime(400)
                .collect {
                    rvBtDevices.adapter?.notifyDataSetChanged()
                }
        }

        viewModel.foundDevices.observe(viewLifecycleOwner) {
            it._macAddress.value?.let { macAddress ->
                devices[macAddress] = it
            }
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                updateDeviceListEvents.emit(true)
            }
        }

        viewModel.scanState.observe(viewLifecycleOwner) {
            when (it) {
                NOT_SCANNING -> {
                    tvScanHeader.text = "Searching finished"
                    if (devices.isEmpty()) {
                        Toast.makeText(
                            context,
                            "No Bluetooth devices found!",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    }
                }
                SCANNING -> tvScanHeader.text = "Searching for BT devices..."
                else -> null //Throws error w/o else branch
            }
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_device_scan_results, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvBtDevices = view.findViewById(R.id.rvBtDevices)
        rvBtDevices.adapter = BtDeviceAdapter(devices)
        rvBtDevices.layoutManager = LinearLayoutManager(context)
        tvScanHeader = view.findViewById(R.id.tvScanHeader)

        swLegacyBt = view.findViewById(R.id.swLegacyScan)
        swLegacyBt.setOnCheckedChangeListener { _, isChecked ->
            viewModel.viewModelScope.launch {
                viewModel.useLegacyModeChannel.emit(isChecked)
            }
        }
    }

    inner class BtDeviceAdapter(private val btDevices: Map<String, DeviceEntity>) :
        RecyclerView.Adapter<BtDeviceAdapter.ViewHolder>() {

        inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
            val textView = listItemView.findViewById<View>(R.id.label) as TextView
            val tvDescription = listItemView.findViewById<View>(R.id.description) as TextView
            val btnConnect = listItemView.findViewById<View>(R.id.btnConnect) as TextView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            var deviceView: View
            val millis = measureTimeMillis {
                deviceView = layoutInflater.inflate(
                    R.layout.device_element,
                    parent,
                    false
                )
            }
            Log.v(LOG_TAG, "${millis}ms")
            return ViewHolder(deviceView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = btDevices.values.toList()[position]

            holder.textView.text = item._name.value
            holder.tvDescription.text = item._macAddress.value
            holder.btnConnect.text = "Link"

            holder.btnConnect.setOnClickListener {
                onDeviceSelected(item)
            }
        }

        override fun getItemCount(): Int {
            return btDevices.size
        }
    }

    fun onDeviceSelected(device: DeviceEntity) {
        viewModel.onDeviceSelected(device)
        findNavController().navigate(R.id.action_deviceScanResultsFragment_to_devices)
    }

}