package nodomain.freeyourgadget.gadgetbridge.devices.baseus

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

private const val BASE_FILTERS =
    "32 00 78 00 07 00 04 00 94 11 32 00 0a 00 01 00 e0 2e f0 00 07 00 02 00 c0 12 5a 00 28 00 01 00 5f 00 a0 00 07 00 01 00"

/**
 * The vendor app's presets, in its order. Each preset is sent as its id followed by the full filter
 * list, copied byte for byte from a capture of the vendor app.
 */
enum class BaseusEqPreset(val id: Int, extraFilters: String, override val label: Int) : LabeledEntry {
    BASEUS_CLASSIC(0x00, "", R.string.baseus_eq_classic),
    HIFI_LIVE(0x03, "64 00 46 00 07 00 01 00 7c 15 5a 00 07 00 01 00", R.string.baseus_eq_hifi_live),
    JAZZ(0x07, "2c 01 a0 00 0a 00 01 00 b8 0b 96 00 04 00 01 00", R.string.soundcore_equalizer_preset_jazz),
    TREBLE_BOOST(0x09, "b8 0b a0 00 07 00 02 00", R.string.sony_equalizer_preset_treble_boost),
    ACOUSTIC(0x0a, "64 00 5a 00 07 00 00 00 b8 0b 46 00 07 00 01 00", R.string.soundcore_equalizer_preset_acoustic),
    ROCK_CLASSIC(0x0b, "64 00 5a 00 07 00 00 00 b8 0b a0 00 07 00 01 00", R.string.baseus_eq_rock_classic),
    ;

    val filters: ByteArray = "$BASE_FILTERS $extraFilters".trim().split(" ")
        .map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        fun fromId(id: Int): BaseusEqPreset? = entries.find { it.id == id }

        fun fromPreference(value: String?): BaseusEqPreset =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: BASEUS_CLASSIC
    }
}
