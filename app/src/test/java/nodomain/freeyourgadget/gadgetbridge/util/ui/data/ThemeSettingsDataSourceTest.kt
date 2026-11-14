package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettingsDataSource.Companion.PREF_ACCENT_COLOR
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettingsDataSource.Companion.PREF_AMOLED_BLACK
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettingsDataSource.Companion.PREF_CONTRASTING_SURFACES
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeSettingsDataSource.Companion.PREF_THEME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = Application::class)
class ThemeSettingsDataSourceTest {
    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var dataSource: ThemeSettingsDataSource

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        sharedPreferences = context.getSharedPreferences("theme_settings_data_source_test", Context.MODE_PRIVATE)
        sharedPreferences.edit(commit = true) { clear() }
        dataSource = ThemeSettingsDataSource(sharedPreferences)
    }

    private fun store(action: SharedPreferences.Editor.() -> Unit) = sharedPreferences.edit(commit = true, action)

    /**
     * Counts the emissions of [flow] while [block] runs.
     */
    private fun countEmissions(flow: Flow<Unit>, block: () -> Unit): Int {
        var count = 0
        val collection = flow.onEach { count++ }.launchIn(CoroutineScope(Dispatchers.Main.immediate))
        block()
        collection.cancel()
        return count
    }

    @Test
    fun defaults() {
        assertEquals(AppTheme.SYSTEM, dataSource.getTheme())
        assertFalse(dataSource.isAmoledBlack())
        assertEquals(AccentColor.DEFAULT, dataSource.getAccentColor())
        assertNull(dataSource.getContrastingSurfaces())
    }

    @Test
    fun storedValuesAreRead() {
        store {
            putString(PREF_THEME, "dark")
            putBoolean(PREF_AMOLED_BLACK, true)
            putString(PREF_ACCENT_COLOR, "teal")
            putBoolean(PREF_CONTRASTING_SURFACES, false)
        }

        assertEquals(AppTheme.DARK, dataSource.getTheme())
        assertTrue(dataSource.isAmoledBlack())
        assertEquals(AccentColor.TEAL, dataSource.getAccentColor())
        assertEquals(false, dataSource.getContrastingSurfaces())
    }

    @Test
    fun booleansStoredAsStringsAreParsed() {
        store {
            putString(PREF_AMOLED_BLACK, "true")
            putString(PREF_CONTRASTING_SURFACES, "true")
        }

        assertTrue(dataSource.isAmoledBlack())
        assertEquals(true, dataSource.getContrastingSurfaces())
    }

    @Test
    fun unknownStoredValuesFallBack() {
        store {
            putString(PREF_THEME, "unknown")
            putString(PREF_ACCENT_COLOR, "unknown")
        }
        assertEquals(AppTheme.SYSTEM, dataSource.getTheme())
        assertEquals(AccentColor.DEFAULT, dataSource.getAccentColor())

        store {
            putString(PREF_THEME, "")
            putString(PREF_ACCENT_COLOR, "")
        }
        assertEquals(AppTheme.SYSTEM, dataSource.getTheme())
        assertEquals(AccentColor.DEFAULT, dataSource.getAccentColor())
    }

    @Test
    fun settersWriteTheirKeys() {
        dataSource.setTheme(AppTheme.DARK)
        dataSource.setAccentColor(AccentColor.TEAL)
        dataSource.setContrastingSurfaces(false)

        assertEquals("dark", sharedPreferences.getString(PREF_THEME, null))
        assertEquals("teal", sharedPreferences.getString(PREF_ACCENT_COLOR, null))
        assertFalse(sharedPreferences.getBoolean(PREF_CONTRASTING_SURFACES, true))
    }

    @Test
    fun themeValuesMatchThePreferenceEntries() {
        assertEquals(
            context.resources.getStringArray(R.array.pref_theme_values).toList(),
            AppTheme.entries.map { it.value },
        )
    }

    @Test
    fun themeSettingsChangesEmitForEachThemeKeyOnly() {
        val count = countEmissions(dataSource.themeSettingsChanges()) {
            store { putString(PREF_THEME, "dark") }
            store { putBoolean(PREF_AMOLED_BLACK, true) }
            store { putString(PREF_ACCENT_COLOR, "teal") }
            store { putBoolean(PREF_CONTRASTING_SURFACES, true) }
            store { putString("other_key", "value") }
        }

        assertEquals(4, count)
    }

    @Test
    fun themeChangesEmitForTheThemeKeyOnly() {
        val count = countEmissions(dataSource.themeChanges()) {
            store { putString(PREF_THEME, "dark") }
            store { putBoolean(PREF_AMOLED_BLACK, true) }
            store { putString(PREF_ACCENT_COLOR, "teal") }
        }

        assertEquals(1, count)
    }

    @Test
    fun changesOnlyEmitWhileCollected() {
        var count = 0
        val collection = dataSource.themeSettingsChanges()
            .onEach { count++ }
            .launchIn(CoroutineScope(Dispatchers.Main.immediate))
        store { putString(PREF_THEME, "dark") }
        collection.cancel()
        store { putString(PREF_THEME, "light") }

        assertEquals(1, count)
    }

    @Test
    fun applicationNightModeIsNotSupportedBeforeAndroid12() {
        assertFalse(dataSource.supportsApplicationNightMode())
    }

    @Test
    @Config(sdk = [35])
    fun applicationNightModeIsSupportedFromAndroid12() {
        assertTrue(dataSource.supportsApplicationNightMode())
    }

    @Test
    @Config(sdk = [35])
    fun clearingThePreferencesEmitsBothChanges() {
        assertEquals(1, countEmissions(dataSource.themeSettingsChanges()) { store { clear() } })
        assertEquals(1, countEmissions(dataSource.themeChanges()) { store { clear() } })
    }
}
