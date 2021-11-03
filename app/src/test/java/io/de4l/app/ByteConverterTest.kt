package io.de4l.app

import io.de4l.app.auth.AuthManager
import io.de4l.app.sensor.RuuviTagData
import io.de4l.app.sensor.RuuviTagParser
import io.de4l.app.util.ByteConverter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito

class ByteConverterTest {


    init {


    }


    @Test
    fun testValidData() {
        val bytes = "0512FC5394C37C0004FFFC040CAC364200CDCBB8334C884F".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val tagData = RuuviTagParser().parseFromPayload(bytes)

        assertEquals(24.3, tagData.temperature, 0.0)
        assertEquals(53.49, tagData.humidity, 0.0)
        assertEquals(100044, tagData.pressure)
        assertEquals(0.004, tagData.accelerationX, 0.0)
        assertEquals(-0.004, tagData.accelerationY, 0.0)
        assertEquals(1.036, tagData.accelerationZ, 0.0)

    }

    @Test
    fun testMaximumValues() {
        val bytes = "057FFFFFFEFFFE7FFF7FFF7FFFFFDEFEFFFECBB8334C884F".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val tagData = RuuviTagParser().parseFromPayload(bytes)

        assertEquals(163.835, tagData.temperature, 0.0)
        assertEquals(163.8350, tagData.humidity, 0.0)
        assertEquals(115534, tagData.pressure)
        assertEquals(32.767, tagData.accelerationX, 0.0)
        assertEquals(32.767, tagData.accelerationY, 0.0)
        assertEquals(32.767, tagData.accelerationZ, 0.0)

    }

    @Test
    fun testMinimumValues() {
        val bytes = "058001000000008001800180010000000000CBB8334C884F".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val tagData = RuuviTagParser().parseFromPayload(bytes)

        assertEquals(-163.835, tagData.temperature, 0.0)
        assertEquals(0.0, tagData.humidity, 0.0)
        assertEquals(50000, tagData.pressure)
        assertEquals(-32.767, tagData.accelerationX, 0.0)
        assertEquals(-32.767, tagData.accelerationY, 0.0)
        assertEquals(-32.767, tagData.accelerationZ, 0.0)

    }

    @Test
    fun parseFromRawData() {
        val rawBytes = "0201061BFF99040505941A5BC7B1FFE0001C043867366F2497ED4DFAE75678".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val bytes = "0505941A5BC7B1FFE0001C043867366F2497ED4DFAE75678".chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

        val tagDataRaw = RuuviTagParser().parseFromRawFormat5(rawBytes)
        val tagData = RuuviTagParser().parseFromPayload(bytes)

        assertEquals(tagDataRaw.temperature, tagData.temperature, 0.0)
        assertEquals(tagDataRaw.humidity, tagData.humidity, 0.0)
        assertEquals(tagDataRaw.pressure, tagData.pressure)
        assertEquals(tagDataRaw.accelerationX, tagData.accelerationX, 0.0)
        assertEquals(tagDataRaw.accelerationY, tagData.accelerationY, 0.0)
        assertEquals(tagDataRaw.accelerationZ, tagData.accelerationZ, 0.0)

    }

}