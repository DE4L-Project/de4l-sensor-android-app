package io.de4l.app.location

import org.joda.time.DateTime

class Location(
    val latitude: Double,
    val longitude: Double,
    val provider: String,
    val timestamp: DateTime,
    val accuracy: Float,
    val speed: Float,
    val bearing: Float,
    val altitude: Double,
) {

}