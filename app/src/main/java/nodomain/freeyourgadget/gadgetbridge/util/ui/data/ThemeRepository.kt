package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import androidx.annotation.MainThread
import kotlinx.coroutines.flow.StateFlow

/**
 * The user's theme settings (theme, AMOLED black, accent color, contrasting surfaces).
 */
interface ThemeRepository {
    /**
     * The current theme settings. Activities compare it with the settings they were themed with, to know when they
     * have to be recreated.
     */
    val themeSettings: StateFlow<ThemeSettings>

    /**
     * The selected theme. Defaults to following the system.
     */
    fun getSelectedTheme(): AppTheme

    fun setSelectedTheme(theme: AppTheme)

    /**
     * The themes that the user can select, in the order that they are offered.
     */
    fun getAvailableThemes(): List<AppTheme>

    /**
     * The selected accent color preset. Defaults to [AccentColor.DEFAULT].
     */
    fun getAccentColor(): AccentColor

    fun setAccentColor(accentColor: AccentColor)

    /**
     * Whether contrasting surfaces are enabled. Unless set by the user, they are enabled for the dark theme only.
     */
    fun areContrastingSurfacesEnabled(dark: Boolean): Boolean

    fun setContrastingSurfacesEnabled(enabled: Boolean)

    fun areDynamicColorsEnabled(): Boolean

    /**
     * Ensures the splash screen follows the theme preference, if manually set to light or dark.
     */
    fun updateApplicationNightMode()

    /**
     * Starts updating the application night mode whenever the selected theme changes. Does nothing if already
     * registered.
     */
    @MainThread
    fun register()

    /**
     * Stops updating the application night mode. Does nothing if not registered.
     */
    @MainThread
    fun unregister()
}
