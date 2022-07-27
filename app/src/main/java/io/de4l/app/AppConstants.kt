package io.de4l.app


class AppConstants {
    companion object {

        const val REQUEST_CODE_PERMISSIONS: Int = 1000

        // AUTH
        const val AUTH_CLIENT_ID = BuildConfig.AUTH_CLIENT_ID
        const val AUTH_REDIRECT_URI = BuildConfig.AUTH_REDIRECT_URI
        const val AUTH_REDIRECT_URI_END_SESSION = BuildConfig.AUTH_REDIRECT_URI_END_SESSION
        val AUTH_SCOPES = arrayOf("openid", "profile")
        const val AUTH_DISCOVERY_URI = BuildConfig.AUTH_DISCOVERY_URI
        const val AUTH_USERNAME_CLAIM_KEY = BuildConfig.AUTH_USERNAME_CLAIM_KEY
        const val AUTH_MQTT_CLAIM_RESOURCE = BuildConfig.AUTH_MQTT_CLAIM_RESOURCE
        const val AUTH_MQTT_CLAIM_ROLE = BuildConfig.AUTH_MQTT_CLAIM_ROLE

        // BLUETOOTH
        const val BT_AIRBEAM2_SOCKET_RF_COMM_UUID = "00001101-0000-1000-8000-00805F9B34FB"


        // ROOM DB
        const val ROOM_DB_NAME = "app-database"

        // LOCATION
        const val LOCATION_INTERVAL_IN_SECONDS = 5
        val LOCATION_MIN_DISTANCE: Float? = 0.0f

        //MQTT
        const val MQTT_SERVER_URL = BuildConfig.MQTT_SERVER_URL

        val MQTT_TOPIC_PATTERN_SENSOR_VALUES =
            if (BuildConfig.DEBUG) "sensors/%s/de4l-app-v2-debug" else "sensors/%s/de4l-app-v2"

        val MQTT_TOPIC_PATTERN_LOCATION_VALUES =
            if (BuildConfig.DEBUG) "locations/%s/de4l-app-debug" else "locations/%s/de4l-app"

        val HEARTBEAT_TOPIC_PATTERN_LOCATION_VALUES =
            if (BuildConfig.DEBUG) "heartbeats/%s/de4l-app-debug" else "heartbeats/%s/de4l-app"

        //UI
        const val SPLASH_SCREEN_DELAY_IN_SECONDS = 1L

        //SERVICE NOTIFICATION
        const val TRACKING_SERVICE_NOTIFICATION_ID = 1001
        const val TRACKING_NOTIFICATION_STOP_ACTION = "notification-stop-action"
        const val FORCE_RECONNECT_ACTION = "force-reconnect-action"

        const val TRACKING_NOTIFICATION_CODE = 2000
        const val TRACKING_NOTIFICATION_CHANNEL_ID = "de4l-tracking"
        const val TRACKING_NOTIFICATION_CHANNEL_NAME = "DE4L Tracking"

        //WEB VIEW
        const val DE4L_INFO_URL = "https://de4l.io/en/about-de4l/"

        //HEARTBEAT
        const val HEARTBEAT_INTERVAL_SECONDS = 60L

        //UPDATE CHECK
        //check only once every XX hours
        const val UPDATE_CHECK_MINIMUM_INTERVAL_HOURS = 12
        const val UPDATE_FLOW_REQUEST_CODE = 3000

    }
}