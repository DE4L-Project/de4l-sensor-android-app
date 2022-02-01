package io.de4l.app.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Ignore
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.R
import io.de4l.app.auth.UserInfo
import io.de4l.app.bluetooth.BluetoothConnectionState
import io.de4l.app.device.DeviceEntity
import io.de4l.app.location.Location
import io.de4l.app.sensor.SensorType
import io.de4l.app.sensor.SensorValue
import io.de4l.app.tracking.TrackingState
import io.de4l.app.ui.event.SensorValueReceivedEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.w3c.dom.Text

@AndroidEntryPoint
class HomeFragment : Fragment(), OnMapReadyCallback {

    private var _connectedDevices: List<DeviceEntity> = ArrayList()

    private val LOG_TAG = HomeFragment::class.java.name

    private val viewModel: HomeViewModel by viewModels()
    private var mapView: MapView? = null

    private lateinit var constraintLayout: ConstraintLayout
//    private lateinit var tvTemperatureValue: TextView
//    private lateinit var tvHumidityValue: TextView
//    private lateinit var tvPm1Value: TextView
//    private lateinit var tvPm25Value: TextView
//    private lateinit var tvPm10Value: TextView

    private lateinit var tvBtConnectHeader: TextView
    private lateinit var btnBtConnect: FloatingActionButton
    private lateinit var tvBtConnectFooter: TextView

    private lateinit var tvUserHeader: TextView
    private lateinit var btnUser: FloatingActionButton
    private lateinit var tvUserFooter: TextView

    private lateinit var tvTrackingHeader: TextView
    private lateinit var btnTracking: FloatingActionButton
    private lateinit var tvTrackingFooter: TextView

    private lateinit var rvDevices: RecyclerView

    private lateinit var tvVersionInfo: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    @ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

//        viewModel.temperature.observe(viewLifecycleOwner) { value ->
//            updateTemperature(value)
//        }
//
//        viewModel.humidity.observe(viewLifecycleOwner) { value ->
//            updateHumidity(value)
//        }
//
//        viewModel.pm1.observe(viewLifecycleOwner) { value ->
//            updatePm1(value)
//        }
//
//        viewModel.pm25.observe(viewLifecycleOwner) { value ->
//            updatePm25(value)
//        }
//
//        viewModel.pm10.observe(viewLifecycleOwner) { value ->
//            updatePm10(value)
//        }

        viewModel.location.observe(viewLifecycleOwner) { location ->
            updateLocation(location)
        }

        viewModel.user.observe(viewLifecycleOwner) { user ->
            updateUser(user)
        }

        viewModel.trackingEnabled.observe(viewLifecycleOwner) { trackingEnabled ->
            btnTracking.isEnabled = trackingEnabled

            if (trackingEnabled && viewModel.trackingState.value == TrackingState.NOT_TRACKING) {
                styleFabButtonInactive(btnTracking)
            }
        }

        viewModel.trackingState.observe(viewLifecycleOwner) { trackingState ->
            when (trackingState) {
                TrackingState.TRACKING -> onStartTracking()
                TrackingState.NOT_TRACKING -> onStopTracking()
            }
        }

        viewModel.connectedDevices.observe(viewLifecycleOwner) { connectedDevices ->
            if (connectedDevices.isNotEmpty()) {
                tvBtConnectFooter.text = connectedDevices[0].name ?: "Unknown"
            }
        }

        viewModel.bluetoothConnectionState.observe(viewLifecycleOwner) { deviceConnectionState ->
            when (deviceConnectionState) {
                BluetoothConnectionState.CONNECTED -> {
                    styleFabButtonActive(btnBtConnect)
                }
                BluetoothConnectionState.CONNECTING -> {
                    tvBtConnectFooter.text = "Connecting..."
                    styleFabButtonInactive(btnBtConnect)
                }
                BluetoothConnectionState.RECONNECTING -> {
                    tvBtConnectFooter.text = "Reconnecting..."
                    styleFabButtonInactive(btnBtConnect)
                }
                BluetoothConnectionState.DISCONNECTED -> {
                    tvBtConnectFooter.text = "Disconnected"
                    styleFabButtonInactive(btnBtConnect)
                }
                null -> {
                    //Not possible in Kotlin, but Java
                    tvBtConnectFooter.text = "Disconnected"
                    styleFabButtonInactive(btnBtConnect)
                }
            }
        }

        constraintLayout = view.findViewById(R.id.homeParentLayout)

        mapView = view.findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

//        tvTemperatureValue = view.findViewById(R.id.tvTemperatureValue)
//        tvHumidityValue = view.findViewById(R.id.tvHumidityValue)
//        tvPm1Value = view.findViewById(R.id.tvPm1Value)
//        tvPm25Value = view.findViewById(R.id.tvPm25Value)
//        tvPm10Value = view.findViewById(R.id.tvPm10Value)

        tvBtConnectHeader = view.findViewById(R.id.tvBtConnectionHeader)
        btnBtConnect = view.findViewById(R.id.btnBtConnection)
        btnBtConnect.setOnClickListener {
            viewModel.onBtConnectClicked()
        }

        tvBtConnectFooter = view.findViewById(R.id.tvBtConnectionFooter)
        tvBtConnectFooter.isSelected = true

        tvUserHeader = view.findViewById(R.id.tvUserHeader)
        btnUser = view.findViewById(R.id.btnUser)
        btnUser.setOnClickListener {
            activity?.let {
                viewModel.onUserButtonClicked(it)
            }
        }

        tvUserFooter = view.findViewById(R.id.tvUserFooter)
        tvUserFooter.isSelected = true

        tvTrackingHeader = view.findViewById(R.id.tvDataTransmissionHeader)
        btnTracking = view.findViewById(R.id.btnDataTransmission)
        btnTracking.setOnClickListener {
            Log.v(LOG_TAG, "btnTracking Clicked!")
            viewModel.onToggleTrackingClicked()
        }
        tvTrackingFooter = view.findViewById(R.id.tvDataTransmissionFooter)

        rvDevices = view.findViewById(R.id.rvDevices)
        rvDevices.layoutManager = LinearLayoutManager(context)
        viewModel.connectedDevices.observe(viewLifecycleOwner) {
            rvDevices.adapter = DeviceAdapter(it, viewLifecycleOwner)
        }

        tvVersionInfo = view.findViewById(R.id.tvVersionInfo)
        tvVersionInfo.text = "Version: ${viewModel.versionInfo}"
    }

    fun onStartTracking() {
        tvTrackingFooter.text = "Active"
        styleFabButtonActive(btnTracking)
    }

    fun onStopTracking() {
        tvTrackingFooter.text = "Inactive"
        if (btnTracking.isEnabled) {
            styleFabButtonInactive(btnTracking)
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    private fun updateUser(user: UserInfo?) {
        val userCapture = user

        if (userCapture != null) {
            tvUserFooter.text = userCapture.username
            styleFabButtonActive(btnUser)
        } else {
            tvUserFooter.text = "Not Logged In"
            styleFabButtonInactive(btnUser)
        }
    }

    private fun updateLocation(location: Location?) {
        location?.let {
            mapView?.getMapAsync {
                it.clear()
                it.addMarker(
                    MarkerOptions()
                        .position(
                            LatLng(
                                location.latitude,
                                location.longitude
                            )
                        )
                )

                val cameraPosition = CameraPosition.Builder()
                    .target(
                        LatLng(
                            location.latitude,
                            location.longitude
                        )
                    )
                    .zoom(18f)
                    .build()

                val cameraUpdate =
                    CameraUpdateFactory
                        .newCameraPosition(cameraPosition)

                it.moveCamera(cameraUpdate)
            }
        }

    }

//    private fun updateTemperature(sensorValue: Double?) {
//        if (sensorValue != null) {
//            tvTemperatureValue.text = String.format("%.2f °C", sensorValue)
//        } else {
//            tvTemperatureValue.text = "-"
//        }
//    }
//
//    private fun updateHumidity(sensorValue: Double?) {
//        if (sensorValue != null) {
//            tvHumidityValue.text = String.format("%.2f  %%", sensorValue)
//        } else {
//            tvHumidityValue.text = "-"
//        }
//    }
//
//    private fun updatePm1(sensorValue: Double?) {
//        if (sensorValue != null) {
//            tvPm1Value.text = String.format("%.0f ppm", sensorValue)
//        } else {
//            tvPm1Value.text = "-"
//        }
//    }
//
//    private fun updatePm25(sensorValue: Double?) {
//        if (sensorValue != null) {
//            tvPm25Value.text = String.format("%.0f ppm", sensorValue)
//        } else {
//            tvPm25Value.text = "-"
//        }
//    }
//
//    private fun updatePm10(sensorValue: Double?) {
//        if (sensorValue != null) {
//            tvPm10Value.text = String.format("%.0f ppm", sensorValue)
//        } else {
//            tvPm10Value.text = "-"
//        }
//    }

    override fun onMapReady(map: GoogleMap?) {
        val isNightMode =
            context?.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isNightMode) {
            map?.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.map_night_style))
        }
    }

    private fun styleFabButtonInactive(fabButton: FloatingActionButton) {
        fabButton.backgroundTintList =
            ColorStateList.valueOf(resources.getColor(R.color.grey))
    }

    private fun styleFabButtonActive(fabButton: FloatingActionButton) {
        fabButton.backgroundTintList =
            ColorStateList.valueOf(resources.getColor(R.color.light_blue_600))
    }

    inner class DeviceAdapter(
        private var _devices: List<DeviceEntity>,
        private val lifecycleOwner: LifecycleOwner
    ) :
        RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {


        inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
            lateinit var item: DeviceEntity
            val tvTemperature: TextView = listItemView.findViewById(R.id.tvTemperatureValue)

//            init {
//                EventBus.getDefault().register(this)
//            }

//            @Subscribe
//            public fun onSensorEvent(event: SensorValueReceivedEvent) {
//                if (event.sensorValue.airBeamId != item.macAddress) {
//                    return
//                }
//
//                if (event.sensorValue.sensorType === SensorType.TEMPERATURE) {
//                    tvTemperature.text = String.format("%.2f °C", event.sensorValue.value)
//                }
//            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            var deviceView: View
            var viewHolder: ViewHolder? = null

            deviceView = layoutInflater.inflate(
                R.layout.layout_airbeam2_values,
                parent,
                false
            )

            deviceView.setOnLongClickListener {
                return@setOnLongClickListener true
            }

            viewHolder = ViewHolder(deviceView)
            return viewHolder
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = _devices[position]
            holder.item = device

            device.sensorValues.asLiveData().observe(lifecycleOwner) {
                holder.tvTemperature.text = String.format("%.2f °C", it?.value)
            }

//            when (device.connectionState) {
//                BluetoothConnectionState.CONNECTED -> {
//                    holder.btnConnectDevice.text = "Disconnect"
//                    holder.btnConnectDevice.isEnabled = true
//                }
//                BluetoothConnectionState.CONNECTING -> {
//                    holder.btnConnectDevice.text = "Connecting..."
//                    holder.btnConnectDevice.isEnabled = false
//                }
//                BluetoothConnectionState.RECONNECTING -> {
//                    holder.btnConnectDevice.text = "Reconnecting..."
//                    holder.btnConnectDevice.isEnabled = false
//                }
//                BluetoothConnectionState.DISCONNECTED -> {
//                    holder.btnConnectDevice.text = "Connect"
////                    holder.btnConnectDevice.isEnabled =
////                        viewModel.connectionState.value == BluetoothConnectionState.DISCONNECTED
//                }
//            }
//
//            device.let {
//                holder.textView.text = it.name
//                holder.tvDescription.text = it.macAddress
//            }
//
//            holder.btnConnectDevice.setOnClickListener {
//
//            }

        }

        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            super.onDetachedFromRecyclerView(recyclerView)
        }

        override fun getItemCount(): Int {
            return _devices.size
        }

        fun setItems(devices: List<DeviceEntity>) {
            _devices = devices
            notifyDataSetChanged()
        }
    }
}