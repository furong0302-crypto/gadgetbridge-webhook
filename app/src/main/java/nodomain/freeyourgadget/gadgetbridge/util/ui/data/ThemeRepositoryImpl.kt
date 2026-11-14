package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import android.app.UiModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import nodomain.freeyourgadget.gadgetbridge.di.ApplicationScope
import javax.inject.Inject

class ThemeRepositoryImpl @Inject constructor(
    private val dataSource: ThemeSettingsDataSource,
    private val uiModeManager: UiModeManager?,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ThemeRepository {
    private var themeChanges: Job? = null

    override val themeSettings: StateFlow<ThemeSettings> = dataSource.themeSettingsChanges()
        .map { readThemeSettings() }
        .stateIn(applicationScope, SharingStarted.Eagerly, readThemeSettings())

    private fun readThemeSettings() = ThemeSettings(
        theme = dataSource.getTheme(),
        amoledBlack = dataSource.isAmoledBlack(),
        accentColor = dataSource.getAccentColor(),
        contrastingSurfaces = dataSource.getContrastingSurfaces(),
    )

    override fun getSelectedTheme(): AppTheme = dataSource.getTheme()

    override fun setSelectedTheme(theme: AppTheme) {
        dataSource.setTheme(theme)
    }

    override fun getAvailableThemes(): List<AppTheme> = AppTheme.entries

    override fun getAccentColor(): AccentColor = dataSource.getAccentColor()

    override fun setAccentColor(accentColor: AccentColor) {
        dataSource.setAccentColor(accentColor)
    }

    override fun areContrastingSurfacesEnabled(dark: Boolean): Boolean =
        readThemeSettings().areContrastingSurfacesEnabled(dark)

    override fun setContrastingSurfacesEnabled(enabled: Boolean) {
        dataSource.setContrastingSurfaces(enabled)
    }

    override fun updateApplicationNightMode() {
        if (!dataSource.supportsApplicationNightMode()) {
            return
        }
        val nightMode = when (getSelectedTheme()) {
            AppTheme.LIGHT -> UiModeManager.MODE_NIGHT_NO
            AppTheme.DARK -> UiModeManager.MODE_NIGHT_YES
            AppTheme.SYSTEM, AppTheme.DYNAMIC -> UiModeManager.MODE_NIGHT_AUTO
        }
        uiModeManager?.setApplicationNightMode(nightMode)
    }

    override fun areDynamicColorsEnabled(): Boolean {
        return getSelectedTheme() == AppTheme.DYNAMIC
    }

    override fun register() {
        if (themeChanges?.isActive == true) return
        themeChanges = dataSource.themeChanges()
            .onEach { updateApplicationNightMode() }
            .launchIn(applicationScope)
    }

    override fun unregister() {
        themeChanges?.cancel()
        themeChanges = null
    }
}
