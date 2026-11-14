@file:JvmName("ThemeStyles")

package nodomain.freeyourgadget.gadgetbridge.util.ui

import android.content.res.Resources
import androidx.annotation.StyleRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AccentColor
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettings

/**
 * The base theme for these settings, in the dark or light version.
 */
@StyleRes
fun ThemeSettings.themeStyle(dark: Boolean, noActionBar: Boolean): Int = when {
    dynamicColorSelected && dark && amoledBlack ->
        if (noActionBar) R.style.GadgetbridgeThemeDynamicDarkAmoled_NoActionBar else R.style.GadgetbridgeThemeDynamicDarkAmoled

    dynamicColorSelected && dark ->
        if (noActionBar) R.style.GadgetbridgeThemeDynamicDark_NoActionBar else R.style.GadgetbridgeThemeDynamicDark

    dynamicColorSelected ->
        if (noActionBar) R.style.GadgetbridgeThemeDynamicLight_NoActionBar else R.style.GadgetbridgeThemeDynamicLight

    dark && amoledBlack ->
        if (noActionBar) R.style.GadgetbridgeThemeBlack_NoActionBar else R.style.GadgetbridgeThemeBlack

    dark ->
        if (noActionBar) R.style.GadgetbridgeThemeDark_NoActionBar else R.style.GadgetbridgeThemeDark

    else ->
        if (noActionBar) R.style.GadgetbridgeTheme_NoActionBar else R.style.GadgetbridgeTheme
}

/**
 * Applies the overlays for these settings on top of the base [themeStyle] in [theme].
 *
 * Dynamic color already ties tab, button and chip colors to the wallpaper, so the accent color only applies to the
 * other themes.
 */
fun ThemeSettings.applyOverlays(theme: Resources.Theme, dark: Boolean) {
    if (!dynamicColorSelected) {
        theme.applyStyle(accentColorOverlay(dark), true)
    }
    if (areContrastingSurfacesEnabled(dark)) {
        theme.applyStyle(contrastingSurfacesOverlay(dark), true)
    }
}

/**
 * The overlay for the accent color preset (Settings > User interface).
 */
@StyleRes
internal fun ThemeSettings.accentColorOverlay(dark: Boolean): Int = when (accentColor) {
    AccentColor.RED -> if (dark) R.style.ThemeOverlay_App_Accent_Red_Dark else R.style.ThemeOverlay_App_Accent_Red_Light
    AccentColor.CORAL -> if (dark) R.style.ThemeOverlay_App_Accent_Coral_Dark else R.style.ThemeOverlay_App_Accent_Coral_Light
    AccentColor.ORANGE -> if (dark) R.style.ThemeOverlay_App_Accent_Orange_Dark else R.style.ThemeOverlay_App_Accent_Orange_Light
    AccentColor.AMBER -> if (dark) R.style.ThemeOverlay_App_Accent_Amber_Dark else R.style.ThemeOverlay_App_Accent_Amber_Light
    AccentColor.LIME -> if (dark) R.style.ThemeOverlay_App_Accent_Lime_Dark else R.style.ThemeOverlay_App_Accent_Lime_Light
    AccentColor.GREEN -> if (dark) R.style.ThemeOverlay_App_Accent_Green_Dark else R.style.ThemeOverlay_App_Accent_Green_Light
    AccentColor.TEAL -> if (dark) R.style.ThemeOverlay_App_Accent_Teal_Dark else R.style.ThemeOverlay_App_Accent_Teal_Light
    AccentColor.CYAN -> if (dark) R.style.ThemeOverlay_App_Accent_Cyan_Dark else R.style.ThemeOverlay_App_Accent_Cyan_Light
    AccentColor.BLUE -> if (dark) R.style.ThemeOverlay_App_Accent_Blue_Dark else R.style.ThemeOverlay_App_Accent_Blue_Light
    AccentColor.INDIGO -> if (dark) R.style.ThemeOverlay_App_Accent_Indigo_Dark else R.style.ThemeOverlay_App_Accent_Indigo_Light
    AccentColor.VIOLET -> if (dark) R.style.ThemeOverlay_App_Accent_Violet_Dark else R.style.ThemeOverlay_App_Accent_Violet_Light
    AccentColor.PINK -> if (dark) R.style.ThemeOverlay_App_Accent_Pink_Dark else R.style.ThemeOverlay_App_Accent_Pink_Light
    AccentColor.WHITE -> if (dark) R.style.ThemeOverlay_App_Accent_White_Dark else R.style.ThemeOverlay_App_Accent_White_Light
}

@StyleRes
internal fun ThemeSettings.contrastingSurfacesOverlay(dark: Boolean): Int = when {
    dynamicColorSelected && dark && amoledBlack -> R.style.ThemeOverlay_App_ContrastingSurfaces_Dynamic_Black
    dynamicColorSelected -> R.style.ThemeOverlay_App_ContrastingSurfaces_Dynamic
    !dark -> R.style.ThemeOverlay_App_ContrastingSurfaces_Light
    amoledBlack -> R.style.ThemeOverlay_App_ContrastingSurfaces_Black
    else -> R.style.ThemeOverlay_App_ContrastingSurfaces_Dark
}
