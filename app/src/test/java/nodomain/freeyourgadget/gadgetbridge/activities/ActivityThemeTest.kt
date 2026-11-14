package nodomain.freeyourgadget.gadgetbridge.activities

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AccentColor
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AppTheme
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = Application::class)
class ActivityThemeTest {
    class ThemedActivity : AppCompatActivity() {
        var recreations = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            ActivityTheme.apply(this, noActionBar, themeSettings)
            super.onCreate(savedInstanceState)
        }

        override fun recreate() {
            recreations++
        }
    }

    class PlainActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            ActivityTheme.apply(this, noActionBar = true, themeSettings = themeSettings)
            super.onCreate(savedInstanceState)
        }
    }

    @Before
    fun reset() {
        themeSettings.value = LIGHT
        noActionBar = false
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun Context.colorOnSurface(): Int {
        val value = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, value, true)
        return value.data
    }

    @Test
    fun appliesTheCurrentSettings() {
        themeSettings.value = LIGHT.copy(theme = AppTheme.DARK)

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()

        assertEquals(ContextCompat.getColor(activity, R.color.primarytext_dark), activity.colorOnSurface())
        assertNotNull(activity.supportActionBar)
    }

    @Test
    fun appliesTheThemeWithoutActionBar() {
        noActionBar = true

        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()

        assertEquals(ContextCompat.getColor(activity, R.color.primarytext_light), activity.colorOnSurface())
        assertNull(activity.supportActionBar)
    }

    @Test
    fun recreatesWhenTheSettingsDifferFromTheAppliedOnes() {
        val activity = Robolectric.buildActivity(ThemedActivity::class.java).setup().get()
        idle()
        assertEquals(0, activity.recreations)

        themeSettings.value = LIGHT.copy(accentColor = AccentColor.TEAL)
        idle()

        assertEquals(1, activity.recreations)
    }

    @Test
    fun themesActivitiesWithoutALifecycle() {
        themeSettings.value = LIGHT.copy(theme = AppTheme.DARK)

        val activity = Robolectric.buildActivity(PlainActivity::class.java).setup().get()

        assertEquals(ContextCompat.getColor(activity, R.color.primarytext_dark), activity.colorOnSurface())
    }

    private companion object {
        val LIGHT = ThemeSettings(AppTheme.LIGHT, amoledBlack = false, AccentColor.RED, contrastingSurfaces = null)

        // The activities are created by reflection, so they can only reach these through static fields
        val themeSettings = MutableStateFlow(LIGHT)
        var noActionBar = false
    }
}
