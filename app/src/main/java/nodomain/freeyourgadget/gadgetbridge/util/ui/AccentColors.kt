@file:JvmName("AccentColors")

package nodomain.freeyourgadget.gadgetbridge.util.ui

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AccentColor

/**
 * The name of this accent color, as shown in the settings.
 */
@StringRes
fun AccentColor.nameRes(): Int = when (this) {
    AccentColor.RED -> R.string.accent_color_red
    AccentColor.CORAL -> R.string.accent_color_coral
    AccentColor.ORANGE -> R.string.accent_color_orange
    AccentColor.AMBER -> R.string.accent_color_amber
    AccentColor.LIME -> R.string.accent_color_lime
    AccentColor.GREEN -> R.string.accent_color_green
    AccentColor.TEAL -> R.string.accent_color_teal
    AccentColor.CYAN -> R.string.accent_color_cyan
    AccentColor.BLUE -> R.string.accent_color_blue
    AccentColor.INDIGO -> R.string.accent_color_indigo
    AccentColor.VIOLET -> R.string.accent_color_violet
    AccentColor.PINK -> R.string.accent_color_pink
    AccentColor.WHITE -> R.string.accent_color_white
}

/**
 * The swatch for this accent color in the color picker: the tab_pill value of its dark or light overlay, matching
 * what users of that theme get.
 */
@ColorInt
fun AccentColor.swatch(dark: Boolean): Int = when (this) {
    AccentColor.RED -> 0xFFE02F00
    AccentColor.CORAL -> if (dark) 0xFFFF5A47 else 0xFFD63A25
    AccentColor.ORANGE -> if (dark) 0xFFF27C1A else 0xFFC25400
    AccentColor.AMBER -> if (dark) 0xFFF5B800 else 0xFF9A6F00
    AccentColor.LIME -> if (dark) 0xFFA8E02F else 0xFF4F7A00
    AccentColor.GREEN -> if (dark) 0xFF2FD07A else 0xFF0F7A40
    AccentColor.TEAL -> if (dark) 0xFF1DE9D0 else 0xFF00786E
    AccentColor.CYAN -> if (dark) 0xFF22C2F0 else 0xFF006E94
    AccentColor.BLUE -> 0xFF2A6FE0
    AccentColor.INDIGO -> 0xFF5252E0
    AccentColor.VIOLET -> 0xFF8A3FE6
    AccentColor.PINK -> 0xFFD02866
    AccentColor.WHITE -> 0xFFFFFFFF
}.toInt()
