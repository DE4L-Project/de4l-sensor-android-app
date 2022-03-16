package io.de4l.app.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainer
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import io.de4l.app.bluetooth.BluetoothDeviceType
import io.de4l.app.device.DeviceEntity
import io.de4l.app.location.LocationValue
import io.de4l.app.sensor.SensorType
import io.de4l.app.tracking.TrackingState

@AndroidEntryPoint
class HomeFragment : Fragment(), OnMapReadyCallback {

    private val LOG_TAG = HomeFragment::class.java.name

    private val viewModel: HomeViewModel by viewModels()
    private var mapView: MapView? = null

    private lateinit var constraintLayout: ConstraintLayout

    private lateinit var tvBtConnectHeader: TextView
    private lateinit var btnBtConnect: FloatingActionButton
    private lateinit var tvBtConnectFooter: TextView

    private lateinit var tvUserHeader: TextView
    private lateinit var btnUser: FloatingActionButton
    private lateinit var tvUserFooter: TextView

    private lateinit var tvTrackingHeader: TextView
    private lateinit var btnTracking: FloatingActionButton
    private lateinit var tvTrackingFooter: TextView

    //    private lateinit var rvDevices: RecyclerView
    private lateinit var rvDeviceLabels: RecyclerView

    private lateinit var tvVersionInfo: TextView
    private lateinit var fragmentContainer: FragmentContainer

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                TrackingState.TRACKING, TrackingState.LOCATION_ONLY -> onStartTracking()
                TrackingState.NOT_TRACKING -> onStopTracking()
            }
        }

        viewModel.connectedDevices.observe(viewLifecycleOwner) { connectedDevices ->
            if (connectedDevices.isNotEmpty()) {
                tvBtConnectFooter.text = connectedDevices.size.toString() + " Sensors"
                styleFabButtonActive(btnBtConnect)
            } else {
                tvBtConnectFooter.text = "No Sensors connected"
                styleFabButtonInactive(btnBtConnect)
            }
        }

        constraintLayout = view.findViewById(R.id.homeParentLayout)

        mapView = view.findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync(this)

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


//        rvDevices = view.findViewById(R.id.rvDevices)
//        rvDevices.layoutManager = LinearLayoutManager(context)
//        viewModel.connectedDevices.observe(viewLifecycleOwner) {
//            rvDevices.adapter = DeviceAdapter(it, viewLifecycleOwner)
//        }

        rvDeviceLabels = view.findViewById(R.id.rvSensorId)
        rvDeviceLabels.layoutManager = LinearLayoutManager(context)
        (rvDeviceLabels.layoutManager as LinearLayoutManager).orientation =
            LinearLayoutManager.HORIZONTAL

        viewModel.connectedDevices.observe(viewLifecycleOwner) {
            rvDeviceLabels.adapter = DeviceTabsAdapter(it, viewLifecycleOwner)
        }

        tvVersionInfo = view.findViewById(R.id.tvVersionInfo)
        tvVersionInfo.text = "Version: ${viewModel.versionInfo}"

        renderUiForDevice(null)

        viewModel.selectedDevice.asLiveData().observe(viewLifecycleOwner) {
            renderUiForDevice(it)
        }
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

        if (userCapture?.isTrackOnlyUser() == true) {
            viewModel.startLocationUpdates()
        } else {
            viewModel.stopLocationUpdates()
        }
    }

    private fun updateLocation(location: LocationValue?) {
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

    override fun onMapReady(map: GoogleMap) {
        val isNightMode =
            requireContext()
                .resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (isNightMode) {
            map?.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.map_night_style
                )
            )
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

    inner class DeviceTabsAdapter(
        private var _devices: List<DeviceEntity>,
        private val lifecycleOwner: LifecycleOwner
    ) :
        RecyclerView.Adapter<DeviceTabsAdapter.ViewHolder>() {
        inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
            inner class DeviceAdapter(
                private var _devices: List<DeviceEntity>,
                private val lifecycleOwner: LifecycleOwner
            ) :
                RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

                inner class ViewHolder(listItemView: View) : RecyclerView.ViewHolder(listItemView) {
                    lateinit var item: DeviceEntity
                    val tvTemperature: TextView = listItemView.findViewById(R.id.tvTemperatureValue)
                    val tvHumidity: TextView = listItemView.findViewById(R.id.tvHumidityValue)
                    val tvPm1: TextView = listItemView.findViewById(R.id.tvPm1Value)
                    val tvPm25: TextView = listItemView.findViewById(R.id.tvPm25Value)
                    val tvPm10: TextView = listItemView.findViewById(R.id.tvPm10Value)

                    val btnDisconnectSensor: ImageButton =
                        listItemView.findViewById(R.id.btnDisconnectSensor)

                    val tvDeviceAddress: TextView = listItemView.findViewById(R.id.tvDeviceAddress)
                    val tvConnectionState: TextView =
                        listItemView.findViewById(R.id.tvConnectionState)

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
                    holder.tvDeviceAddress.text =
                        device._macAddress.value + " - " + (device._name.value ?: "UNKNOWN")

                    holder.btnDisconnectSensor.setOnClickListener {
                        viewModel.disconnectDevice(device)
                    }


                    device._actualConnectionState.asLiveData().observe(viewLifecycleOwner) {
                        holder.tvConnectionState.text = it.toString() ?: "null"
                    }

                    device._sensorValues.asLiveData().observe(lifecycleOwner) {
                        when (it?.sensorType) {
                            SensorType.TEMPERATURE -> holder.tvTemperature.text =
                                String.format("%.2f °C", it.value)
                            SensorType.HUMIDITY -> holder.tvHumidity.text =
                                String.format("%.2f  %%", it.value)
                            SensorType.PM1 -> holder.tvPm1.text =
                                String.format("%.0f ppm", it.value)
                            SensorType.PM2_5 -> holder.tvPm25.text =
                                String.format("%.0f ppm", it.value)
                            SensorType.PM10 -> holder.tvPm10.text =
                                String.format("%.0f ppm", it.value)
                            null -> {
                                holder.tvTemperature.text = "-"
                                holder.tvHumidity.text = "-"
                                holder.tvPm1.text = "-"
                                holder.tvPm25.text = "-"
                                holder.tvPm10.text = "-"
                            }
                        }
                    }
                }

                override fun getItemCount(): Int {
                    return _devices.size
                }

                fun setItems(devices: List<DeviceEntity>) {
                    _devices = devices
                    notifyDataSetChanged()
                }
            }

            lateinit var item: DeviceEntity
            var btnDevice: Button = listItemView.findViewById(R.id.btnDevice)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            var deviceView: View
            var viewHolder: ViewHolder? = null

            deviceView = layoutInflater.inflate(
                R.layout.layout_sensor_selection,
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
            holder.btnDevice.text =
                (device._name.value ?: "UNKNOWN ${device.getBluetoothDeviceType()}")

            holder.btnDevice.setOnClickListener {
                viewModel.onDeviceButtonClicked(device)
            }
        }

        override fun getItemCount(): Int {
            return _devices.size
        }

        fun setItems(devices: List<DeviceEntity>) {
            _devices = devices
            notifyDataSetChanged()
        }
    }

    fun renderUiForDevice(deviceEntity: DeviceEntity?) {
        val sensorValueFragment = when (deviceEntity?.getBluetoothDeviceType()) {
            BluetoothDeviceType.NONE -> EmptySensorValueFragment()
            BluetoothDeviceType.AIRBEAM2 -> AirBeamSensorValueFragment(deviceEntity)
            BluetoothDeviceType.AIRBEAM3 -> AirBeamSensorValueFragment(deviceEntity)
            BluetoothDeviceType.RUUVI_TAG -> RuuviSensorValueFragment(deviceEntity)
            null -> {
                EmptySensorValueFragment()
            }
        }

        childFragmentManager
            .beginTransaction()
            .replace(R.id.fragmentContainerView, sensorValueFragment)
            .commit()
    }
}