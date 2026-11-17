package nodomain.freeyourgadget.gadgetbridge.service.devices.baseus

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_BASS_BOOST
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_BASEUS_LOW_LATENCY
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_HEADPHONES_EQUALIZER
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.devices.baseus.BaseusEqPreset
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import org.slf4j.LoggerFactory

/**
 * Baseus Bowie MC1 SPP protocol, mapped from an HCI capture of the vendor app.
 *
 * Requests are `BA <id>`, replies and pushes are `AA <id> <payload>`. There is no length byte, so
 * replies are split using the lengths seen in the capture.
 */
object BaseusProtocol {
    private val LOG = LoggerFactory.getLogger(BaseusProtocol::class.java)

    private const val REQUEST: Byte = 0xBA.toByte()
    private const val REPLY: Byte = 0xAA.toByte()

    /** Asks for the full status dump the vendor app requests when it opens, case battery included. */
    private const val ID_STATUS = 0x01
    private const val ID_BUDS_BATTERY = 0x02
    private const val ID_LOW_LATENCY_STATE = 0x23
    private const val ID_SET_LOW_LATENCY = 0x24
    private const val ID_CASE_BATTERY = 0x27
    private const val ID_EQ_PRESET = 0x30
    private const val ID_SET_EQ = 0x31
    private const val ID_BASS_BOOST_STATE = 0x53
    private const val ID_SET_BASS_BOOST = 0x54

    const val BATTERY_INDEX_LEFT = 0
    const val BATTERY_INDEX_RIGHT = 1
    const val BATTERY_INDEX_CASE = 2

    /** Total frame length per reply id, `AA` and the id included, as seen in the capture. */
    private val FRAME_LENGTHS = mapOf(
        0x02 to 6, 0x19 to 7, 0x21 to 5, 0x23 to 3, 0x24 to 3, 0x27 to 4,
        0x30 to 3, 0x31 to 3, 0x36 to 3, 0x42 to 3, 0x53 to 3, 0x54 to 3,
    )

    fun encodeGetStatus(): ByteArray = byteArrayOf(REQUEST, ID_STATUS.toByte())

    fun encodeGetBudsBattery(): ByteArray = byteArrayOf(REQUEST, ID_BUDS_BATTERY.toByte())

    /** Acked with `AA 54 01`; the new state follows as `AA 53`. */
    fun encodeSetBassBoost(enabled: Boolean): ByteArray = encodeSwitch(ID_SET_BASS_BOOST, enabled)

    /** Acked with `AA 24 01`; the new state follows as `AA 23`. */
    fun encodeSetLowLatency(enabled: Boolean): ByteArray = encodeSwitch(ID_SET_LOW_LATENCY, enabled)

    /** Acked with `AA 31 01`; no state push follows. */
    fun encodeSetEqPreset(preset: BaseusEqPreset): ByteArray =
        byteArrayOf(REQUEST, ID_SET_EQ.toByte(), preset.id.toByte()) + preset.filters

    private fun encodeSwitch(id: Int, enabled: Boolean) =
        byteArrayOf(REQUEST, id.toByte(), if (enabled) 1 else 0)

    fun decode(data: ByteArray): List<GBDeviceEvent> {
        val events = mutableListOf<GBDeviceEvent>()
        var pos = 0
        while (pos + 1 < data.size) {
            val id = data[pos + 1].toInt() and 0xFF
            val length = FRAME_LENGTHS[id]
            if (data[pos] != REPLY || length == null || pos + length > data.size) {
                LOG.warn("Unknown or truncated frame, dropping {} bytes", data.size - pos)
                break
            }
            when (id) {
                // AA 02 <left> 00 <right> 01
                ID_BUDS_BATTERY -> {
                    events.add(battery(BATTERY_INDEX_LEFT, data[pos + 2]))
                    events.add(battery(BATTERY_INDEX_RIGHT, data[pos + 4]))
                }
                // AA 27 <case> 00
                ID_CASE_BATTERY -> events.add(battery(BATTERY_INDEX_CASE, data[pos + 2]))
                // AA 53 / AA 23 <0|1>, in the status dump and after every change
                ID_BASS_BOOST_STATE -> events.add(switchState(PREF_BASEUS_BASS_BOOST, data[pos + 2]))
                ID_LOW_LATENCY_STATE -> events.add(switchState(PREF_BASEUS_LOW_LATENCY, data[pos + 2]))
                // AA 30 <preset id>, in the status dump; custom EQ (0x65) has no preset entry
                ID_EQ_PRESET -> BaseusEqPreset.fromId(data[pos + 2].toInt() and 0xFF)?.let {
                    events.add(GBDeviceEventUpdatePreferences(PREF_HEADPHONES_EQUALIZER, it.name.lowercase()))
                }
            }
            pos += length
        }
        return events
    }

    private fun switchState(key: String, raw: Byte) = GBDeviceEventUpdatePreferences(key, raw.toInt() == 1)

    /** A bud in the closed case reports 0, so 0 means unavailable, as in other earbud drivers. */
    private fun battery(index: Int, raw: Byte): GBDeviceEventBatteryInfo {
        val level = raw.toInt() and 0xFF
        val known = level in 1..100
        return GBDeviceEventBatteryInfo().apply {
            batteryIndex = index
            this.level = if (known) level else GBDevice.BATTERY_UNKNOWN.toInt()
            state = if (known) BatteryState.BATTERY_NORMAL else BatteryState.UNKNOWN
        }
    }
}
