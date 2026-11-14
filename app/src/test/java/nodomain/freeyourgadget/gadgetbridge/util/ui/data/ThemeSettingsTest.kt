package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import android.app.Application
import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = Application::class)
class ThemeSettingsTest {
    private fun settings(theme: AppTheme, contrastingSurfaces: Boolean? = null) =
        ThemeSettings(theme, amoledBlack = false, AccentColor.RED, contrastingSurfaces = contrastingSurfaces)

    private fun configuration(night: Boolean) = Configuration().apply {
        uiMode = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }

    @Test
    fun lightAndDarkIgnoreTheSystemNightMode() {
        assertFalse(settings(AppTheme.LIGHT).isDark(configuration(night = true)))
        assertTrue(settings(AppTheme.DARK).isDark(configuration(night = false)))
    }

    @Test
    fun systemAndDynamicAreLightBeforeAndroid10() {
        assertFalse(settings(AppTheme.SYSTEM).isDark(configuration(night = true)))
        assertFalse(settings(AppTheme.DYNAMIC).isDark(configuration(night = true)))
    }

    @Test
    @Config(sdk = [35])
    fun systemAndDynamicFollowTheSystemNightMode() {
        assertTrue(settings(AppTheme.SYSTEM).isDark(configuration(night = true)))
        assertFalse(settings(AppTheme.SYSTEM).isDark(configuration(night = false)))
        assertTrue(settings(AppTheme.DYNAMIC).isDark(configuration(night = true)))
        assertFalse(settings(AppTheme.DYNAMIC).isDark(configuration(night = false)))
    }

    @Test
    fun dynamicColorIsSelectedByTheDynamicThemeOnly() {
        assertTrue(settings(AppTheme.DYNAMIC).dynamicColorSelected)
        AppTheme.entries.filter { it != AppTheme.DYNAMIC }.forEach {
            assertFalse(settings(it).dynamicColorSelected)
        }
    }

    @Test
    fun contrastingSurfacesFollowDarkUntilSet() {
        assertTrue(settings(AppTheme.SYSTEM).areContrastingSurfacesEnabled(dark = true))
        assertFalse(settings(AppTheme.SYSTEM).areContrastingSurfacesEnabled(dark = false))

        assertTrue(settings(AppTheme.SYSTEM, contrastingSurfaces = true).areContrastingSurfacesEnabled(dark = false))
        assertFalse(settings(AppTheme.SYSTEM, contrastingSurfaces = false).areContrastingSurfacesEnabled(dark = true))
    }
}
