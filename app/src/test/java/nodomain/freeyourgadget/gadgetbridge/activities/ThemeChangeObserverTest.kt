package nodomain.freeyourgadget.gadgetbridge.activities

import android.app.Application
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AccentColor
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AppTheme
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = Application::class)
class ThemeChangeObserverTest {
    class RecordingActivity : ComponentActivity() {
        var recreations = 0

        override fun onCreate(savedInstanceState: Bundle?) {
            lifecycle.addObserver(ThemeChangeObserver(this, themeSettings, themeSettings.value))
            super.onCreate(savedInstanceState)
        }

        override fun recreate() {
            recreations++
        }
    }

    @Before
    fun resetThemeSettings() {
        themeSettings.value = INITIAL_SETTINGS
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun sameSettingsDoNotRecreate() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).setup().get()

        themeSettings.value = themeSettings.value.copy()
        idle()

        assertEquals(0, activity.recreations)
    }

    @Test
    fun changedSettingsRecreateAStartedActivity() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).setup().get()

        themeSettings.value = themeSettings.value.copy(theme = AppTheme.DARK)
        idle()

        assertEquals(1, activity.recreations)
    }

    /**
     * A change made in a click (e.g. a dialog button) must not recreate the activity before the click finishes, or a
     * dialog that is still being dismissed is saved and shown again after the recreation.
     */
    @Test
    fun recreatesAfterTheCurrentMessage() {
        val activity = Robolectric.buildActivity(RecordingActivity::class.java).setup().get()
        idle()

        themeSettings.value = themeSettings.value.copy(accentColor = AccentColor.TEAL)
        assertEquals(0, activity.recreations)

        idle()
        assertEquals(1, activity.recreations)
    }

    @Test
    fun settingsChangedWhileStoppedRecreateWhenStartedAgain() {
        val controller = Robolectric.buildActivity(RecordingActivity::class.java).setup()
        controller.pause().stop()

        themeSettings.value = themeSettings.value.copy(accentColor = AccentColor.TEAL)
        idle()
        assertEquals(0, controller.get().recreations)

        controller.start()
        idle()
        assertEquals(1, controller.get().recreations)
    }

    @Test
    fun settingsChangedBackBeforeStartingAgainDoNotRecreate() {
        val controller = Robolectric.buildActivity(RecordingActivity::class.java).setup()
        controller.pause().stop()

        val applied = themeSettings.value
        themeSettings.value = applied.copy(amoledBlack = true)
        themeSettings.value = applied
        controller.start()
        idle()

        assertEquals(0, controller.get().recreations)
    }

    private companion object {
        val INITIAL_SETTINGS =
            ThemeSettings(AppTheme.SYSTEM, amoledBlack = false, AccentColor.RED, contrastingSurfaces = null)

        // The activity is created by reflection, so it can only reach the flow through a static field
        val themeSettings = MutableStateFlow(INITIAL_SETTINGS)
    }
}
