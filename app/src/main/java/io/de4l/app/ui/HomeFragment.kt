package io.de4l.app.ui

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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
import io.de4l.app.location.Location
import io.de4l.app.tracking.TrackingState
import kotlinx.coroutines.ExperimentalCoroutinesApi

@AndroidEntryPoint
class HomeFragment : Fragment(), OnMapReadyCallback {

    private val LOG_TAG = HomeFragment::class.java.name

    private val viewModel: HomeViewModel by viewModels()
    private var mapView: MapView? = null

    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var tvTemperatureValue: TextView
    private lateinit var tvHumidityValue: TextView
    private lateinit var tvPm1Value: TextView
    private lateinit var tvPm25Value: TextView
    private lateinit var tvPm10Value: TextView

    private lateinit var tvBtConnectHeader: TextView
    private lateinit var btnBtConnect: FloatingActionButton
    private lateinit var tvBtConnectFooter: TextView

    private lateinit var tvUserHeader: TextView
    private lateinit var btnUser: FloatingActionButton
    private lateinit var tvUserFooter: TextView

    private lateinit var tvTrackingHeader: TextView
    private lateinit var btnTracking: FloatingActionButton
    private lateinit var tvTrackingFooter: TextView

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

        viewModel.temperature.observe(viewLifecycleOwner) { value ->
            updateTemperature(value)
        }

        viewModel.humidity.observe(viewLifecycleOwner) { value ->
            updateHumidity(value)
        }

        viewModel.pm1.observe(viewLifecycleOwner) { value ->
            updatePm1(value)
        }

        viewModel.pm25.observe(viewLifecycleOwner) { value ->
            updatePm25(value)
        }

        viewModel.pm10.observe(viewLifecycleOwner) { value ->
            updatePm10(value)
        }

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

        tvTemperatureValue = view.findViewById(R.id.tvTemperatureValue)
        tvHumidityValue = view.findViewById(R.id.tvHumidityValue)
        tvPm1Value = view.findViewById(R.id.tvPm1Value)
        tvPm25Value = view.findViewById(R.id.tvPm25Value)
        tvPm10Value = view.findViewById(R.id.tvPm10Value)

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

    private fun updateTemperature(sensorValue: Double?) {
        if (sensorValue != null) {
            tvTemperatureValue.text = String.format("%.2f °C", sensorValue)
        } else {
            tvTemperatureValue.text = "-"
        }
    }

    private fun updateHumidity(sensorValue: Double?) {
        if (sensorValue != null) {
            tvHumidityValue.text = String.format("%.2f  %%", sensorValue)
        } else {
            tvHumidityValue.text = "-"
        }
    }

    private fun updatePm1(sensorValue: Double?) {
        if (sensorValue != null) {
            tvPm1Value.text = String.format("%.0f ppm", sensorValue)
        } else {
            tvPm1Value.text = "-"
        }
    }

    private fun updatePm25(sensorValue: Double?) {
        if (sensorValue != null) {
            tvPm25Value.text = String.format("%.0f ppm", sensorValue)
        } else {
            tvPm25Value.text = "-"
        }
    }

    private fun updatePm10(sensorValue: Double?) {
        if (sensorValue != null) {
            tvPm10Value.text = String.format("%.0f ppm", sensorValue)
        } else {
            tvPm10Value.text = "-"
        }
    }

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
}