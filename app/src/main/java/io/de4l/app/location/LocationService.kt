package io.de4l.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.google.android.gms.location.*
import io.de4l.app.AppConstants
import io.de4l.app.location.event.LocationUpdateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.joda.time.DateTime

class LocationService() {
    private val LOG_TAG: String = LocationService::class.java.getName()

    private var mLocationCallback: LocationCallback? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null

    private val mLocationRequest = LocationRequest.create()
        .setInterval(AppConstants.LOCATION_INTERVAL_IN_SECONDS * 1000L)
        .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

    private var locations: MutableList<Location> = ArrayList()

    private var coroutineScope: CoroutineScope? = null

    init {
        AppConstants.LOCATION_MIN_DISTANCE?.let {
            mLocationRequest.setSmallestDisplacement(it)
        }
    }

    fun addLocation(location: Location) {
        this.locations.add(location)
    }

    fun getCurrentLocation(): Location? {
        if (locations.isEmpty()) {
            return null
        }
        return this.locations.last()
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(context: Context) {
        coroutineScope = CoroutineScope(Dispatchers.Default)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                coroutineScope?.launch {
                    super.onLocationResult(locationResult)
                    if (locationResult !== null) {
                        val location = Location(
                            locationResult.lastLocation.latitude,
                            locationResult.lastLocation.longitude,
                            locationResult.lastLocation.provider,
                            DateTime(locationResult.lastLocation.time),
                            locationResult.lastLocation.accuracy
                        )

                        val locationUpdateEvent = LocationUpdateEvent(location)
                        addLocation(location)
                        EventBus.getDefault().post(locationUpdateEvent)
                        Log.i(
                            LOG_TAG,
                            "${Thread.currentThread().name} | ${locationResult.lastLocation.provider} [${locationResult.lastLocation.longitude}; ${locationResult.lastLocation.latitude}]"
                        )
                    }
                }
            }
        }

        // Don't need looper when using Kotlin coroutines
        mFusedLocationClient?.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            null
        )
    }

    fun stopLocationUpdates() {
        mFusedLocationClient?.removeLocationUpdates(mLocationCallback)
        coroutineScope?.cancel()
        coroutineScope = null
    }


}