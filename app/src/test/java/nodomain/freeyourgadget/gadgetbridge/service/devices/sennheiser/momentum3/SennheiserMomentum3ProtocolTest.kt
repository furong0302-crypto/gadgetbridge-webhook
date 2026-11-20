package nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3

import nodomain.freeyourgadget.gadgetbridge.service.devices.sennheiser.momentum3.SennheiserMomentum3Protocol.Frame
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Frames taken from a Bluetooth HCI snoop of the official app talking to a MOMENTUM 3 Wireless. */
class SennheiserMomentum3ProtocolTest {
    @Test
    fun encodesRequests() {
        assertArrayEquals(
            GB.hexStringToByteArray("ebfe0100010000000400ee00010016002a"),
            SennheiserMomentum3Protocol.encode(0x01, 0x16),
        )
        assertArrayEquals(
            GB.hexStringToByteArray("ebfe0100010000000600c9000600320001002a"),
            SennheiserMomentum3Protocol.encode(0x06, 0x32, byteArrayOf(0x01, 0x00)),
        )
    }

    @Test
    fun escapesTerminatorAndEscapeByte() {
        assertArrayEquals(
            GB.hexStringToByteArray("ebfe010001000000080030000a000e0000003f3f3f2f2a"),
            SennheiserMomentum3Protocol.encode(0x0a, 0x0e, GB.hexStringToByteArray("00003f2a")),
        )
    }

    @Test
    fun decodesSplitAndEscapedFrames() {
        val decoder = SennheiserMomentum3Protocol.Decoder()
        val stream = GB.hexStringToByteArray(
            "2aebfe0100010000000a001400010016000400000008002a" +
                "ebfe010001000000080030000a000e0000003f3f3f2f2a" +
                "ebfe0100100100000800820017000e0002000a002a"
        )
        val frames = decoder.decode(stream.copyOfRange(0, 30)) + decoder.decode(stream.copyOfRange(30, stream.size))
        assertEquals(
            listOf("0001 01 16 040000000800", "0001 0a 0e 00003f2a", "0110 17 0e 02000a00"),
            frames.map(::describe),
        )
    }

    @Test
    fun dropsFramesWithBadChecksum() {
        val decoder = SennheiserMomentum3Protocol.Decoder()
        assertEquals(emptyList<Frame>(), decoder.decode(GB.hexStringToByteArray("ebfe0100010000000400ef00010016002a")))
    }

    private fun describe(frame: Frame): String = String.format(
        "%04x %02x %02x %s", frame.type, frame.group, frame.command,
        frame.data.joinToString("") { String.format("%02x", it) },
    )
}
