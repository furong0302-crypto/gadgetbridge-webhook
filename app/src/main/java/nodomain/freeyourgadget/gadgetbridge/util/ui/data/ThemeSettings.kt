package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import android.content.res.Configuration
import android.os.Build

/**
 * The stored settings that an activity's theme is built from. When they change, activities have to be recreated to
 * apply them.
 *
 * @property contrastingSurfaces null until the user sets it, so that it follows the dark theme
 */
data class ThemeSettings(
    val theme: AppTheme,
    val amoledBlack: Boolean,
    val accentColor: AccentColor,
    val contrastingSurfaces: Boolean?,
) {
    /**
     * Whether dynamic color is selected. It is only active if the system also supports it.
     */
    val dynamicColorSelected: Boolean
        get() = theme == AppTheme.DYNAMIC

    /**
     * Whether the theme comes out dark under [configuration]. The system and dynamic themes follow the system night
     * mode from Android 10 on, and are light before.
     */
    fun isDark(configuration: Configuration): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && (theme == AppTheme.SYSTEM || theme == AppTheme.DYNAMIC)) {
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        } else {
            theme == AppTheme.DARK
        }

    /**
     * Whether contrasting surfaces are enabled. Unless set by the user, they are enabled when [dark] only.
     */
    fun areContrastingSurfacesEnabled(dark: Boolean): Boolean = contrastingSurfaces ?: dark
}
