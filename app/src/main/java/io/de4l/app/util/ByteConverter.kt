package io.de4l.app.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    }
}