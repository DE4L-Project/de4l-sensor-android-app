package io.de4l.app.sensor

import io.de4l.app.util.ByteConverter

class RuuviTagParser {
    private val PAYLOAD_DATA_START_INDEX = 4

    private val TEMPERATURE_START_INDEX = 1
    private val TEMPERATURE_BYTES = 2
    private val TEMPERATURE_FACTOR = 0.005

    private val HUMIDITY_START_INDEX = 3
    private val HUMIDITY_BYTES = 2
    private val HUMIDITY_FACTOR = 0.0025

    private val PRESSURE_START_INDEX = 5
    private val PRESSURE_BYTES = 2
    private val PRESSURE_OFFSET = 50000

    private val ACCELERATION_START_INDEX = 7
    private val ACCELERATION_BYTES = 2
    private val ACCELERATION_FACTOR = 1000.0


    fun parseFromRawFormat5(bytes: ByteArray): RuuviTagData {
        val headerLength = bytes!![0].toInt() + 1

        return parseFromPayload(
            bytes.copyOfRange(
                headerLength + PAYLOAD_DATA_START_INDEX,
                bytes.size
            )
        )
    }


    fun parseFromPayload(bytes: ByteArray): RuuviTagData {
        val temperature = ByteConverter.bytesToShort(
            bytes.copyOfRange(
                TEMPERATURE_START_INDEX,
                TEMPERATURE_START_INDEX + TEMPERATURE_BYTES
            )
        ).toInt() * TEMPERATURE_FACTOR

        val humidity = ByteConverter.bytesToUnsignedShort(
            bytes.copyOfRange(
                HUMIDITY_START_INDEX,
                HUMIDITY_START_INDEX + HUMIDITY_BYTES
            )
        ).toInt() * HUMIDITY_FACTOR

        val pressure =
            ByteConverter.bytesToUnsignedShort(
                bytes.copyOfRange(
                    PRESSURE_START_INDEX,
                    PRESSURE_START_INDEX + PRESSURE_BYTES
                )
            ).toInt() + PRESSURE_OFFSET

        val accelerationX = ByteConverter.bytesToShort(
            bytes.copyOfRange(
                ACCELERATION_START_INDEX,
                ACCELERATION_START_INDEX + ACCELERATION_BYTES
            )
        ).toInt() / ACCELERATION_FACTOR

        val accelerationY = ByteConverter.bytesToShort(
            bytes.copyOfRange(
                ACCELERATION_START_INDEX + ACCELERATION_BYTES,
                ACCELERATION_START_INDEX + ACCELERATION_BYTES * 2
            )
        ).toInt() / ACCELERATION_FACTOR

        val accelerationZ = ByteConverter.bytesToShort(
            bytes.copyOfRange(
                ACCELERATION_START_INDEX + ACCELERATION_BYTES * 2,
                ACCELERATION_START_INDEX + ACCELERATION_BYTES * 3
            )
        ).toInt() / ACCELERATION_FACTOR

        return RuuviTagData(
            temperature,
            humidity,
            pressure,
            accelerationX,
            accelerationY,
            accelerationZ
        )
    }

}