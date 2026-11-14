package nodomain.freeyourgadget.gadgetbridge.activities

import android.app.Activity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettings

/**
 * Recreates an activity when the theme settings differ from [appliedSettings], the ones it was themed with. It only
 * checks while the activity is started, so an activity in the background is recreated when it is shown again.
 *
 * The activity is recreated after the current UI event finishes, not during it. A setting changed from a dialog button
 * would otherwise recreate the activity before the dialog is dismissed, and the recreated activity would show the
 * dialog again.
 *
 * [ActivityTheme.apply] attaches it with the same settings that it applies.
 */
class ThemeChangeObserver(
    private val activity: Activity,
    private val themeSettings: StateFlow<ThemeSettings>,
    private val appliedSettings: ThemeSettings,
) : DefaultLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                themeSettings.first { it != appliedSettings }
                activity.window.decorView.post(activity::recreate)
            }
        }
    }
}
