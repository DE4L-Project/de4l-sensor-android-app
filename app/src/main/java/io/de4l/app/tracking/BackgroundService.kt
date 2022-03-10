package io.de4l.app.tracking

import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.de4l.app.AppConstants
import io.de4l.app.R
import io.de4l.app.auth.AuthManager
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.bluetooth.event.ConnectToBluetoothDeviceEvent
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.LocationService
import io.de4l.app.mqtt.MqttManager
import io.de4l.app.ui.MainActivity
import io.de4l.app.ui.event.StartTrackingServiceEvent
import io.de4l.app.ui.event.StopTrackingServiceEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.merge
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundService() : Service() {

    private val LOG_TAG: String = BackgroundService::class.java.getName()

    @Inject
    lateinit var locationService: LocationService

    @Inject
    lateinit var bluetoothDeviceManager: BluetoothDeviceManager

    @Inject
    lateinit var mqttManager: MqttManager

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var deviceRepository: DeviceRepository

    @Inject
    lateinit var backgroundServiceWatcher: BackgroundServiceWatcher

    @Inject
    lateinit var trackingManager: TrackingManager

    private lateinit var notificationManager: NotificationManager

    private var mBroadcastReceiver: BroadcastReceiver? = null

    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(p0: Intent?): IBinder? {
        return null;
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.v(LOG_TAG, "onStartCommand()")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        locationService.startLocationUpdates(this)

        coroutineScope.launch {

            Log.v(LOG_TAG, "coroutineScope launch()")
            merge(
                bluetoothDeviceManager.hasConnectedDevices(),
                trackingManager.trackingState,
                authManager.user
            ).collect {
                // Update notification when any of these changed
                Log.v(LOG_TAG, "updateNotification()")
                updateNotification()
            }
        }

        coroutineScope.launch {
            var lastValue: Boolean? = null
            val connectedDevices = deviceRepository.getDevicesShouldBeConnected().firstOrNull()
            connectedDevices?.forEach { device ->
                val macAddress = device._macAddress.value
                macAddress?.let {
                    launch { bluetoothDeviceManager.connectDeviceWithRetry(it) }
                }
            }
        }
        coroutineScope.launch {

//            bluetoothDeviceManager.hasConnectedDevices().collect {
//                when (it) {
//                    true -> {
//                        deviceRepository.getDevicesShouldBeConnected().collect { devices ->
//                            devices.forEach {
//                                it._macAddress.value?.let { macAddress ->
//                                    launch {
//                                        bluetoothDeviceManager.connectDeviceWithRetry(
//                                            macAddress
//                                        )
//                                    }
//                                }
//                            }
//                        }
//                    }
//                    false -> {
//                        if (lastValue !== null && lastValue == true) {
//                            stopSelf()
//                        }
//                    }
//                }
//                lastValue = it
//            }
        }

        if (!backgroundServiceWatcher.isBackgroundServiceActive.value) {
            backgroundServiceWatcher.isBackgroundServiceActive.value = true
            startForeground(
                AppConstants.TRACKING_SERVICE_NOTIFICATION_ID,
                buildNotification("Background service started")
            )
        }

        return START_STICKY
    }

    override fun onCreate() {
        Log.v(LOG_TAG, "onCreate()")
        super.onCreate()
        registerBroadcastReceivers()
        EventBus.getDefault().register(this)
    }

    private fun registerBroadcastReceivers() {
        mBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                when (p1?.action) {
                    AppConstants.TRACKING_NOTIFICATION_STOP_ACTION -> {
                        Log.i(LOG_TAG, AppConstants.TRACKING_NOTIFICATION_STOP_ACTION)
                        stopSelf()
                    }
                    AppConstants.FORCE_RECONNECT_ACTION -> {
//                        bluetoothDeviceManager.forceReconnect()
                    }
                }

            }
        }
        registerReceiver(
            mBroadcastReceiver,
            IntentFilter(AppConstants.TRACKING_NOTIFICATION_STOP_ACTION)
        )

        registerReceiver(
            mBroadcastReceiver,
            IntentFilter(AppConstants.FORCE_RECONNECT_ACTION)
        )
    }

    override fun onDestroy() {
        Log.v(LOG_TAG, "onDestroy()")
        coroutineScope.cancel()
        backgroundServiceWatcher.isBackgroundServiceActive.value = false
        EventBus.getDefault().unregister(this)
        locationService.stopLocationUpdates()
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver)
        }

        bluetoothDeviceManager.reset()

        CoroutineScope(Dispatchers.Default)
            .launch {
                Log.v(LOG_TAG, "coroutineScope - close - start")

                trackingManager.stopTracking()
                Log.v(LOG_TAG, "coroutineScope - close - end")
                Log.v(LOG_TAG, "coroutineScope - close - after")
                stopForeground(true)
                super.onDestroy()
            }
    }

    private suspend fun updateNotification() {
        var trackingStateText = "No data transmission"
        var bluetoothConnectionText = "No device connected"

        if (trackingManager.trackingState.value == TrackingState.TRACKING ||
            trackingManager.trackingState.value == TrackingState.LOCATION_ONLY
        ) {
            trackingStateText = "Transmitting data"
        }

        when (bluetoothDeviceManager.hasConnectedDevices().firstOrNull()) {
            true -> {
                val connectedDevices =
                    deviceRepository.getConnectedDevices().filterNot { it.isEmpty() }
                        .firstOrNull()

                connectedDevices?.let {
                    bluetoothConnectionText = "${connectedDevices.size} connected"

                }
            }
//            BluetoothConnectionState.CONNECTING -> bluetoothConnectionText = "Connecting..."
//            BluetoothConnectionState.RECONNECTING -> bluetoothConnectionText = "Reconnecting..."
        }

        val notificationText = "$bluetoothConnectionText | $trackingStateText"


        val notification = buildNotification(notificationText)
        notificationManager.notify(1001, notification)
    }

    private fun buildNotification(message: String): Notification {
        val intent = Intent(AppConstants.TRACKING_NOTIFICATION_STOP_ACTION)
        val activityActionPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this,
            1002,
            intent,
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )

        val forceReconnectIntent = Intent(AppConstants.FORCE_RECONNECT_ACTION)
        val forceReconnectPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this,
            1003,
            forceReconnectIntent,
            FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                AppConstants.TRACKING_NOTIFICATION_CHANNEL_ID,
                AppConstants.TRACKING_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            AppConstants.TRACKING_NOTIFICATION_CODE,
            notificationIntent, FLAG_IMMUTABLE
        )

        val notification: Notification =
            NotificationCompat.Builder(this, AppConstants.TRACKING_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("DE4L")
                .setContentText(message)
                .setSmallIcon(R.drawable.ic_data_transmission_24)
                .setContentIntent(pendingIntent)
                .addAction(
                    R.drawable.googleg_standard_color_18,
                    "Stop",
                    activityActionPendingIntent
                )
//                .addAction(
//                    R.drawable.googleg_standard_color_18,
//                    "Force Reconnect",
//                    forceReconnectPendingIntent
//                )
                .build()


        return notification
    }


    @ExperimentalCoroutinesApi
    @Subscribe
    fun onStartTrackingServiceEvent(event: StartTrackingServiceEvent) {
        coroutineScope.launch {
            trackingManager.startTracking()
        }
    }

    @Subscribe
    fun onStopTrackingServiceEvent(event: StopTrackingServiceEvent) {
        coroutineScope.launch {
            trackingManager.stopTracking()
        }
    }

    @Subscribe
    fun onConnectToBluetoothDevice(event: ConnectToBluetoothDeviceEvent) {
        coroutineScope.launch {
            if (event.connectWithRetry) {
                bluetoothDeviceManager.connectDeviceWithRetry(event.macAddress)
            } else {
                bluetoothDeviceManager.connectDevice(event.macAddress)
            }
        }
    }


}