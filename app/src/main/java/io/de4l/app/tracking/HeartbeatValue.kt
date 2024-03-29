package io.de4l.app.tracking

import org.joda.time.DateTime


class HeartbeatValue(
    val timestamp: DateTime,
    val appId: String,
    val batteryPercentage: Int?,
    val isPowerSaveMode: Boolean?,
) {

}
