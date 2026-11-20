package nodomain.freeyourgadget.gadgetbridge.devices.sennheiser

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/** The noise cancelling modes with their codes; transparent hearing replaces noise cancelling. */
enum class SennheiserMomentum3NoiseControl(
    override val label: Int,
    @StringRes val shortLabel: Int,
    @DrawableRes val icon: Int,
    val code: Int?,
) : LabeledEntry {
    MAXIMUM(R.string.sennheiser_momentum_3_noise_control_maximum, R.string.sennheiser_momentum_3_noise_control_maximum_short, R.drawable.ic_noise_control_on, 1),
    ANTI_WIND(R.string.sennheiser_momentum_3_noise_control_anti_wind, R.string.sennheiser_momentum_3_noise_control_anti_wind, R.drawable.ic_air, 3),
    ANTI_PRESSURE(R.string.sennheiser_momentum_3_noise_control_anti_pressure, R.string.sennheiser_momentum_3_noise_control_anti_pressure_short, R.drawable.ic_pressure, 4),
    TRANSPARENT_HEARING(R.string.sennheiser_momentum_3_noise_control_transparent_hearing, R.string.sony_ambient_sound_ambient_sound_short, R.drawable.ic_hearing, null);

    companion object {
        fun fromCode(code: Int): SennheiserMomentum3NoiseControl? = entries.firstOrNull { it.code == code }

        fun fromPreference(value: String?): SennheiserMomentum3NoiseControl? =
            entries.firstOrNull { it.name.lowercase() == value }
    }
}
