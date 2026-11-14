package nodomain.freeyourgadget.gadgetbridge.util.ui

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AccentColor
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AppTheme
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeStylesTest {
    private fun settings(theme: AppTheme = AppTheme.SYSTEM, amoledBlack: Boolean = false, accentColor: AccentColor = AccentColor.RED) =
        ThemeSettings(theme, amoledBlack, accentColor, contrastingSurfaces = null)

    @Test
    fun themeStyleForTheStaticThemes() {
        assertEquals(R.style.GadgetbridgeTheme, settings().themeStyle(dark = false, noActionBar = false))
        assertEquals(R.style.GadgetbridgeTheme_NoActionBar, settings().themeStyle(dark = false, noActionBar = true))
        assertEquals(R.style.GadgetbridgeThemeDark, settings().themeStyle(dark = true, noActionBar = false))
        assertEquals(R.style.GadgetbridgeThemeDark_NoActionBar, settings().themeStyle(dark = true, noActionBar = true))

        val black = settings(amoledBlack = true)
        assertEquals(R.style.GadgetbridgeTheme, black.themeStyle(dark = false, noActionBar = false))
        assertEquals(R.style.GadgetbridgeThemeBlack, black.themeStyle(dark = true, noActionBar = false))
        assertEquals(R.style.GadgetbridgeThemeBlack_NoActionBar, black.themeStyle(dark = true, noActionBar = true))
    }

    @Test
    fun themeStyleForDynamicColor() {
        val dynamic = settings(AppTheme.DYNAMIC)
        assertEquals(R.style.GadgetbridgeThemeDynamicLight, dynamic.themeStyle(dark = false, noActionBar = false))
        assertEquals(
            R.style.GadgetbridgeThemeDynamicLight_NoActionBar,
            dynamic.themeStyle(dark = false, noActionBar = true),
        )
        assertEquals(R.style.GadgetbridgeThemeDynamicDark, dynamic.themeStyle(dark = true, noActionBar = false))
        assertEquals(R.style.GadgetbridgeThemeDynamicDark_NoActionBar, dynamic.themeStyle(dark = true, noActionBar = true))

        val black = settings(AppTheme.DYNAMIC, amoledBlack = true)
        assertEquals(R.style.GadgetbridgeThemeDynamicLight, black.themeStyle(dark = false, noActionBar = false))
        assertEquals(R.style.GadgetbridgeThemeDynamicDarkAmoled, black.themeStyle(dark = true, noActionBar = false))
        assertEquals(
            R.style.GadgetbridgeThemeDynamicDarkAmoled_NoActionBar,
            black.themeStyle(dark = true, noActionBar = true),
        )
    }

    @Test
    fun accentColorOverlay() {
        assertEquals(R.style.ThemeOverlay_App_Accent_Red_Light, settings().accentColorOverlay(false))
        assertEquals(R.style.ThemeOverlay_App_Accent_Red_Dark, settings().accentColorOverlay(true))
        val teal = settings(accentColor = AccentColor.TEAL)
        assertEquals(R.style.ThemeOverlay_App_Accent_Teal_Dark, teal.accentColorOverlay(true))
        assertEquals(R.style.ThemeOverlay_App_Accent_Teal_Light, teal.accentColorOverlay(false))
        assertEquals(
            R.style.ThemeOverlay_App_Accent_White_Dark,
            settings(accentColor = AccentColor.WHITE).accentColorOverlay(true),
        )
    }

    @Test
    fun contrastingSurfacesOverlay() {
        val dark = settings(AppTheme.DARK)
        assertEquals(R.style.ThemeOverlay_App_ContrastingSurfaces_Light, dark.contrastingSurfacesOverlay(false))
        assertEquals(R.style.ThemeOverlay_App_ContrastingSurfaces_Dark, dark.contrastingSurfacesOverlay(true))

        val black = settings(AppTheme.DARK, amoledBlack = true)
        assertEquals(R.style.ThemeOverlay_App_ContrastingSurfaces_Light, black.contrastingSurfacesOverlay(false))
        assertEquals(R.style.ThemeOverlay_App_ContrastingSurfaces_Black, black.contrastingSurfacesOverlay(true))

        val dynamicBlack = settings(AppTheme.DYNAMIC, amoledBlack = true)
        assertEquals(R.style.ThemeOverlay_App_ContrastingSurfaces_Dynamic, dynamicBlack.contrastingSurfacesOverlay(false))
        assertEquals(
            R.style.ThemeOverlay_App_ContrastingSurfaces_Dynamic_Black,
            dynamicBlack.contrastingSurfacesOverlay(true),
        )

        assertEquals(
            R.style.ThemeOverlay_App_ContrastingSurfaces_Dynamic,
            settings(AppTheme.DYNAMIC).contrastingSurfacesOverlay(true),
        )
    }
}
