package nodomain.freeyourgadget.gadgetbridge.service.devices.baseus

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_BASS_BOOST
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_LOW_LATENCY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.devices.baseus.BaseusEqPreset
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BaseusProtocolTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun levels(data: ByteArray) =
        BaseusProtocol.decode(data).filterIsInstance<GBDeviceEventBatteryInfo>().associate { it.batteryIndex to it.level }

    /** Catches left and right being swapped or read from the marker bytes. */
    @Test
    fun budsBatteryReply() {
        assertEquals(
            mapOf(BaseusProtocol.BATTERY_INDEX_LEFT to 80, BaseusProtocol.BATTERY_INDEX_RIGHT to 60),
            levels(bytes(0xAA, 0x02, 0x50, 0x00, 0x3C, 0x01)),
        )
    }

    /** Catches the case level being lost when it arrives in one read with other replies, as on lid open. */
    @Test
    fun caseBatteryAfterOtherRepliesInOneRead() {
        val read = bytes(0xAA, 0x42, 0x00, 0xAA, 0x21, 0x00, 0x0C, 0x0B, 0xAA, 0x27, 0x5A, 0x00, 0xAA, 0x30, 0x00)
        assertEquals(mapOf(BaseusProtocol.BATTERY_INDEX_CASE to 90), levels(read))
    }

    /** Catches a bud in the closed case (reported as 0) showing as a flat 0% battery. */
    @Test
    fun budInClosedCaseIsUnknown() {
        assertEquals(
            mapOf(BaseusProtocol.BATTERY_INDEX_LEFT to -1, BaseusProtocol.BATTERY_INDEX_RIGHT to 90),
            levels(bytes(0xAA, 0x02, 0x00, 0x00, 0x5A, 0x01)),
        )
    }

    /** Catches a crash or garbage levels from an unknown or truncated frame. */
    @Test
    fun unknownOrTruncatedFrameIsDropped() {
        assertEquals(emptyMap<Int, Int>(), levels(bytes(0xAA, 0x77, 0x01, 0xAA, 0x02, 0x50)))
        assertEquals(emptyMap<Int, Int>(), levels(bytes(0xAA, 0x02, 0x50)))
    }

    /** Catches the bass boost command bytes drifting from the captured `BA 54 00` / `BA 54 01`. */
    @Test
    fun encodeBassBoost() {
        assertArrayEquals(bytes(0xBA, 0x54, 0x01), BaseusProtocol.encodeSetBassBoost(true))
        assertArrayEquals(bytes(0xBA, 0x54, 0x00), BaseusProtocol.encodeSetBassBoost(false))
    }

    /** Catches the switch not following the buds: the ack must be skipped and the state applied. */
    @Test
    fun bassBoostStateAfterAck() {
        val events = BaseusProtocol.decode(bytes(0xAA, 0x54, 0x01, 0xAA, 0x53, 0x00))
        assertEquals(1, events.size)
        assertEquals(false, (events[0] as GBDeviceEventUpdatePreferences).preferences[PREF_BASEUS_BASS_BOOST])
    }

    /** Catches low latency being sent with the bass boost id, or its state landing on the wrong switch. */
    @Test
    fun lowLatencyCommandAndState() {
        assertArrayEquals(bytes(0xBA, 0x24, 0x01), BaseusProtocol.encodeSetLowLatency(true))
        val events = BaseusProtocol.decode(bytes(0xAA, 0x24, 0x01, 0xAA, 0x23, 0x01))
        assertEquals(1, events.size)
        assertEquals(mapOf<String, Any>(PREF_BASEUS_LOW_LATENCY to true), (events[0] as GBDeviceEventUpdatePreferences).preferences)
    }

    /** Catches a typo in the copied filter bytes: Hi-Fi Live must match the captured frame exactly. */
    @Test
    fun encodeEqPresetMatchesCapture() {
        val captured = "ba 31 03 32 00 78 00 07 00 04 00 94 11 32 00 0a 00 01 00 e0 2e f0 00 07 00 02 00 " +
            "c0 12 5a 00 28 00 01 00 5f 00 a0 00 07 00 01 00 64 00 46 00 07 00 01 00 7c 15 5a 00 07 00 01 00"
        val expected = captured.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, BaseusProtocol.encodeSetEqPreset(BaseusEqPreset.HIFI_LIVE))
    }

    /** Catches the preset list not showing the buds' preset on connect, and custom EQ (0x65) being misread. */
    @Test
    fun eqPresetState() {
        val jazz = BaseusProtocol.decode(bytes(0xAA, 0x30, 0x07))
        assertEquals("jazz", (jazz.single() as GBDeviceEventUpdatePreferences).preferences[PREF_HEADPHONES_EQUALIZER])
        assertEquals(0, BaseusProtocol.decode(bytes(0xAA, 0x30, 0x65)).size)
    }
}
