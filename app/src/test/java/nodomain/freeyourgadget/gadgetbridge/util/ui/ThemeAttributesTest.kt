package nodomain.freeyourgadget.gadgetbridge.util.ui

import android.app.Application
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The theme attributes that code resolves with [MaterialColors] instead of hard-coding light and dark colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = Application::class)
class ThemeAttributesTest {
    private val app: Context = RuntimeEnvironment.getApplication()

    private fun themed(style: Int): Context = ContextThemeWrapper(app, style)

    private fun Context.attr(attr: Int) = MaterialColors.getColor(this, attr, Color.MAGENTA)

    private fun Context.color(id: Int) = ContextCompat.getColor(this, id)

    /**
     * Code resolves these with the throwing [MaterialColors.getColor] overloads, so every app theme must define them.
     */
    @Test
    fun everyAppThemeDefinesTheResolvedAttributes() {
        val themes = listOf(
            R.style.GadgetbridgeTheme,
            R.style.GadgetbridgeTheme_NoActionBar,
            R.style.GadgetbridgeThemeDark,
            R.style.GadgetbridgeThemeDark_NoActionBar,
            R.style.GadgetbridgeThemeBlack,
            R.style.GadgetbridgeThemeBlack_NoActionBar,
            R.style.GadgetbridgeThemeDynamicLight,
            R.style.GadgetbridgeThemeDynamicLight_NoActionBar,
            R.style.GadgetbridgeThemeDynamicDark,
            R.style.GadgetbridgeThemeDynamicDark_NoActionBar,
            R.style.GadgetbridgeThemeDynamicDarkAmoled,
            R.style.GadgetbridgeThemeDynamicDarkAmoled_NoActionBar,
        )
        val attributes = listOf(R.attr.textColorPrimary, R.attr.textColorSecondary, android.R.attr.colorBackground)

        for (style in themes) {
            val context = themed(style)
            for (attr in attributes) {
                // Throws if the theme doesn't define the attribute
                MaterialColors.getColor(context, attr, app.resources.getResourceName(style))
            }
        }
    }

    @Test
    fun textColorsOfTheLightTheme() {
        val context = themed(R.style.GadgetbridgeTheme)

        assertEquals(context.color(R.color.primarytext_light), context.attr(R.attr.textColorPrimary))
        assertEquals(context.color(R.color.on_surface_variant_light), context.attr(R.attr.textColorSecondary))
    }

    @Test
    fun textColorsOfTheDarkThemes() {
        for (style in listOf(R.style.GadgetbridgeThemeDark, R.style.GadgetbridgeThemeBlack)) {
            val context = themed(style)

            assertEquals(context.color(R.color.primarytext_dark), context.attr(R.attr.textColorPrimary))
            assertEquals(context.color(R.color.on_surface_variant_dark), context.attr(R.attr.textColorSecondary))
        }
    }

    @Test
    fun textColorsFollowColorOnSurfaceInTheDynamicThemes() {
        for (style in listOf(R.style.GadgetbridgeThemeDynamicLight, R.style.GadgetbridgeThemeDynamicDark)) {
            val context = themed(style)

            assertEquals(
                context.attr(com.google.android.material.R.attr.colorOnSurface),
                context.attr(R.attr.textColorPrimary),
            )
            assertEquals(
                context.attr(com.google.android.material.R.attr.colorOnSurfaceVariant),
                context.attr(R.attr.textColorSecondary),
            )
        }
    }

    @Test
    fun textColorsAreKeptByTheContrastingSurfacesOverlay() {
        val context = themed(R.style.GadgetbridgeThemeDark)
        context.theme.applyStyle(R.style.ThemeOverlay_App_ContrastingSurfaces_Dark, true)

        assertEquals(context.color(R.color.primarytext_dark), context.attr(R.attr.textColorPrimary))
        assertEquals(context.color(R.color.on_surface_variant_dark), context.attr(R.attr.textColorSecondary))
    }

    @Test
    fun colorBackgroundIsTheWindowBackground() {
        assertEquals(
            app.color(R.color.window_background_light),
            themed(R.style.GadgetbridgeTheme).attr(android.R.attr.colorBackground),
        )
        assertEquals(
            app.color(R.color.window_background_dark),
            themed(R.style.GadgetbridgeThemeDark).attr(android.R.attr.colorBackground),
        )
        assertEquals(Color.BLACK, themed(R.style.GadgetbridgeThemeBlack).attr(android.R.attr.colorBackground))
    }

    /**
     * Charts used to read this attribute as their background, which made them transparent. ChartTheme now says so
     * explicitly.
     */
    @Test
    fun themesDoNotSetABackground() {
        val value = TypedValue()

        assertFalse(themed(R.style.GadgetbridgeTheme).theme.resolveAttribute(android.R.attr.background, value, true))
        assertFalse(themed(R.style.GadgetbridgeThemeDark).theme.resolveAttribute(android.R.attr.background, value, true))
    }
}
