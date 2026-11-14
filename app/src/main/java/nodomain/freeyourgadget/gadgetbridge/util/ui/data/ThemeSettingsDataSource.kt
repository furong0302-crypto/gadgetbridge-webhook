package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.content.edit
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import nodomain.freeyourgadget.gadgetbridge.di.DefaultPrefs
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the theme settings in the app's default [SharedPreferences]. Only this class knows their keys and stored
 * values.
 *
 * Reads are tolerant of the stored type in the same way as [Prefs], e.g. a boolean stored as a string is parsed.
 */
@Singleton
class ThemeSettingsDataSource @Inject constructor(
    @DefaultPrefs private val preferences: SharedPreferences,
) {
    private val prefs = Prefs(preferences)

    /**
     * The selected theme. Missing or unknown values follow the system.
     */
    fun getTheme(): AppTheme = AppTheme.fromValue(prefs.getString(PREF_THEME, null))

    fun setTheme(theme: AppTheme) {
        preferences.edit { putString(PREF_THEME, theme.value) }
    }

    fun isAmoledBlack(): Boolean = prefs.getBoolean(PREF_AMOLED_BLACK, false)

    /**
     * The selected accent color. Missing or unknown values fall back to [AccentColor.DEFAULT].
     */
    fun getAccentColor(): AccentColor = AccentColor.fromValue(prefs.getString(PREF_ACCENT_COLOR, null))

    fun setAccentColor(accentColor: AccentColor) {
        preferences.edit { putString(PREF_ACCENT_COLOR, accentColor.value) }
    }

    /**
     * Whether contrasting surfaces are enabled, or null until the user sets it.
     */
    fun getContrastingSurfaces(): Boolean? =
        if (preferences.contains(PREF_CONTRASTING_SURFACES)) prefs.getBoolean(PREF_CONTRASTING_SURFACES, false) else null

    fun setContrastingSurfaces(enabled: Boolean) {
        preferences.edit { putBoolean(PREF_CONTRASTING_SURFACES, enabled) }
    }

    /**
     * Whether the system keeps a night mode per application, from Android 12 on.
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun supportsApplicationNightMode(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Emits whenever one of the theme settings changes while the flow is collected.
     */
    fun themeSettingsChanges(): Flow<Unit> = changedKeys()
        .filter { it == null || it in THEME_SETTINGS_KEYS }
        .map { }

    /**
     * Emits whenever the selected theme changes while the flow is collected.
     */
    fun themeChanges(): Flow<Unit> = changedKeys()
        .filter { it == null || it == PREF_THEME }
        .map { }

    /**
     * The keys that change while the flow is collected. A null key means that the file was cleared, which is only
     * reported on Android 11 and later.
     */
    private fun changedKeys(): Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.buffer(Channel.UNLIMITED)

    companion object {
        const val PREF_THEME = "pref_key_theme"
        const val PREF_AMOLED_BLACK = "pref_key_theme_amoled_black"
        const val PREF_ACCENT_COLOR = "pref_key_accent_color"
        const val PREF_CONTRASTING_SURFACES = "pref_key_contrasting_surfaces"

        private val THEME_SETTINGS_KEYS =
            setOf(PREF_THEME, PREF_AMOLED_BLACK, PREF_ACCENT_COLOR, PREF_CONTRASTING_SURFACES)
    }
}
