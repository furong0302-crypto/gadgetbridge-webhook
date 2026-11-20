package nodomain.freeyourgadget.gadgetbridge.devices.sennheiser

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/** Bass, mid and treble in dB, as the official app applies them. */
enum class SennheiserMomentum3EqualizerPreset(
    override val label: Int,
    val bass: Double,
    val mid: Double,
    val treble: Double,
) : LabeledEntry {
    CUSTOM(R.string.custom, 0.0, 0.0, 0.0),
    ROCK(R.string.nothing_equalizer_rock, 0.0, 3.0, 0.0),
    POP(R.string.nothing_equalizer_pop, 0.0, 2.0, 3.0),
    DANCE(R.string.soundcore_equalizer_preset_dance, 2.0, -2.0, 2.0),
    HIP_HOP(R.string.soundcore_equalizer_preset_hip_hop, 3.0, 2.0, 0.0),
    CLASSICAL(R.string.nothing_equalizer_classical, -2.0, 0.0, 1.0),
    MOVIE(R.string.sennheiser_momentum_3_equalizer_movie, 0.7, 1.2, -2.2);

    companion object {
        fun fromPreference(value: String?): SennheiserMomentum3EqualizerPreset =
            entries.firstOrNull { it.name.lowercase() == value } ?: CUSTOM
    }
}
