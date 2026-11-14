package nodomain.freeyourgadget.gadgetbridge.activities

import android.app.Activity
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow
import nodomain.freeyourgadget.gadgetbridge.util.ui.applyOverlays
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettings
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeEntryPoint
import nodomain.freeyourgadget.gadgetbridge.util.ui.themeStyle

/**
 * Themes activities from the user's theme settings.
 */
object ActivityTheme {
    /**
     * Themes [activity] from the current theme settings, and recreates it whenever they change while it is started.
     * Activities that aren't a [LifecycleOwner] are only themed.
     *
     * Call it in `onCreate`, before `super.onCreate`.
     */
    @JvmStatic
    fun apply(activity: Activity, noActionBar: Boolean) {
        apply(activity, noActionBar, ThemeEntryPoint.themeRepository(activity).themeSettings)
    }

    internal fun apply(activity: Activity, noActionBar: Boolean, themeSettings: StateFlow<ThemeSettings>) {
        val applied = themeSettings.value
        val dark = applied.isDark(activity.resources.configuration)
        activity.setTheme(applied.themeStyle(dark, noActionBar))
        applied.applyOverlays(activity.theme, dark)
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(ThemeChangeObserver(activity, themeSettings, applied))
        }
    }
}
