package io.de4l.app.sensor

class RuuviTagData(
    var temperature: Double,
    var humidity: Double,
    var pressure: Int,
    var accelerationX: Double,
    var accelerationY: Double,
    var accelerationZ: Double,
) {
    override fun toString(): String {
        return "RuuviTagData(temperature=$temperature, humidity=$humidity, pressure=$pressure, accelerationX=$accelerationX, accelerationY=$accelerationY, accelerationZ=$accelerationZ)"
    }
}