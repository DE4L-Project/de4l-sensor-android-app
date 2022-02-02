package io.de4l.app.util

import org.apache.commons.codec.binary.Hex
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class ByteConverter {
    companion object {
        fun bytesToShort(
            bytes: ByteArray,
            byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
        ): Short {
            return convert(2, bytes, byteOrder).getShort(0)
        }

        fun bytesToUnsignedShort(
            bytes: ByteArray,
            byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN,
        ): UShort {
            return convert(2, bytes, byteOrder).getShort(0).toUShort()
        }

        private fun convert(
            allocationSize: Int,
            bytes: ByteArray,
            byteOrder: ByteOrder
        ): ByteBuffer {
            val byteBuffer: ByteBuffer = ByteBuffer.allocate(allocationSize)
            byteBuffer.order(byteOrder)

            for (byte: Byte in bytes) {
                byteBuffer.put(byte)
            }

            return byteBuffer
        }

        fun asciiToHex(asciiString: String): String {
            return String(Hex.encodeHex(asciiString.toByteArray(StandardCharsets.UTF_8)))
        }

        fun asciiToBytArray(asciiString: String): ByteArray {
            return hexToByteArray(asciiToHex(asciiString))
        }

        fun hexToByteArray(hexString: String): ByteArray {
            return hexString.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()
        }

        fun bytesToAscii(bytes: ByteArray): String {
            return String(bytes)
        }


    }
}