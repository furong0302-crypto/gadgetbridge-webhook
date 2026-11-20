package nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3

import java.io.ByteArrayOutputStream

/**
 * The MOMENTUM 3 Wireless speaks its own protocol over the "Nautilus" serial port service. A frame is
 * `EB FE 0001 type 0000 length crc group command data`, every field a little-endian u16, followed by
 * the terminator 0x2A. The length counts group, command and data; the CRC-8 covers the same bytes.
 * Requests and their responses use type 0x0001, unsolicited notifications 0x0110. Inside a frame,
 * 0x2A and the escape byte 0x3F are sent as `3F 2F` and `3F 3F`.
 */
object SennheiserMomentum3Protocol {
    const val TYPE_REQUEST = 0x0001
    const val TYPE_NOTIFICATION = 0x0110

    private const val MAGIC_0 = 0xEB
    private const val MAGIC_1 = 0xFE
    private const val VERSION = 0x0001
    private const val TERMINATOR = 0x2A
    private const val ESCAPE = 0x3F
    private const val ESCAPED_TERMINATOR = 0x2F
    private const val HEADER_LENGTH = 12

    class Frame(val type: Int, val group: Int, val command: Int, val data: ByteArray) {
        val isNotification: Boolean get() = type == TYPE_NOTIFICATION
    }

    fun encode(group: Int, command: Int, data: ByteArray = byteArrayOf()): ByteArray {
        val payload = u16(group) + u16(command) + data
        val raw = byteArrayOf(MAGIC_0.toByte(), MAGIC_1.toByte()) +
            u16(VERSION) + u16(TYPE_REQUEST) + u16(0) +
            u16(payload.size) + u16(crc8(payload)) + payload
        val out = ByteArrayOutputStream(raw.size + 4)
        for (b in raw) {
            when (b.toInt() and 0xFF) {
                TERMINATOR -> { out.write(ESCAPE); out.write(ESCAPED_TERMINATOR) }
                ESCAPE -> { out.write(ESCAPE); out.write(ESCAPE) }
                else -> out.write(b.toInt())
            }
        }
        out.write(TERMINATOR)
        return out.toByteArray()
    }

    fun u16(value: Int): ByteArray = byteArrayOf(value.toByte(), (value shr 8).toByte())

    fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    fun crc8(data: ByteArray): Int {
        var crc = 0xFF
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }
        return crc
    }

    /**
     * Splits the byte stream at the terminator, which never occurs inside a frame, so the decoder
     * resynchronises after garbage. Incomplete frames are kept for the next call.
     */
    class Decoder {
        private val pending = ByteArrayOutputStream()

        fun decode(bytes: ByteArray): List<Frame> {
            val frames = mutableListOf<Frame>()
            for (b in bytes) {
                val value = b.toInt() and 0xFF
                if (value != TERMINATOR) {
                    pending.write(value)
                    continue
                }
                val chunk = pending.toByteArray()
                pending.reset()
                parse(unescape(chunk))?.let { frames.add(it) }
            }
            return frames
        }

        private fun unescape(chunk: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(chunk.size)
            var i = 0
            while (i < chunk.size) {
                val value = chunk[i].toInt() and 0xFF
                if (value == ESCAPE && i + 1 < chunk.size) {
                    val next = chunk[i + 1].toInt() and 0xFF
                    out.write(if (next == ESCAPED_TERMINATOR) TERMINATOR else next)
                    i += 2
                } else {
                    out.write(value)
                    i++
                }
            }
            return out.toByteArray()
        }

        private fun parse(raw: ByteArray): Frame? {
            if (raw.size < HEADER_LENGTH + 4 ||
                raw[0].toInt() and 0xFF != MAGIC_0 || raw[1].toInt() and 0xFF != MAGIC_1
            ) {
                return null
            }
            val length = readU16(raw, 8)
            if (raw.size != HEADER_LENGTH + length) {
                return null
            }
            val payload = raw.copyOfRange(HEADER_LENGTH, raw.size)
            if (crc8(payload) != readU16(raw, 10)) {
                return null
            }
            return Frame(
                type = readU16(raw, 4),
                group = readU16(payload, 0),
                command = readU16(payload, 2),
                data = payload.copyOfRange(4, payload.size),
            )
        }
    }
}
