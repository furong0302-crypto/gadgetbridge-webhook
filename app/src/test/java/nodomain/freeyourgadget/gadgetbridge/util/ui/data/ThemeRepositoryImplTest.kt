package nodomain.freeyourgadget.gadgetbridge.util.ui.data

import android.app.UiModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeRepositoryImplTest {
    private lateinit var dataSource: ThemeSettingsDataSource
    private lateinit var uiModeManager: UiModeManager
    private lateinit var applicationScope: CoroutineScope
    private lateinit var themeSettingsChanges: MutableSharedFlow<Unit>
    private lateinit var themeChanges: MutableSharedFlow<Unit>
    private lateinit var repository: ThemeRepositoryImpl

    @Before
    fun setUp() {
        dataSource = mock(ThemeSettingsDataSource::class.java)
        uiModeManager = mock(UiModeManager::class.java)
        applicationScope = CoroutineScope(UnconfinedTestDispatcher())
        themeSettingsChanges = MutableSharedFlow(extraBufferCapacity = 1)
        themeChanges = MutableSharedFlow(extraBufferCapacity = 1)

        `when`(dataSource.getTheme()).thenReturn(AppTheme.SYSTEM)
        `when`(dataSource.getAccentColor()).thenReturn(AccentColor.RED)
        // Mockito answers false for a boxed Boolean, but the user hasn't set contrasting surfaces yet
        `when`(dataSource.getContrastingSurfaces()).thenReturn(null)
        `when`(dataSource.supportsApplicationNightMode()).thenReturn(true)
        `when`(dataSource.themeSettingsChanges()).thenReturn(themeSettingsChanges)
        `when`(dataSource.themeChanges()).thenReturn(themeChanges)

        repository = ThemeRepositoryImpl(dataSource, uiModeManager, applicationScope)
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
    }

    private fun storeTheme(theme: AppTheme) {
        `when`(dataSource.getTheme()).thenReturn(theme)
    }

    @Test
    fun themeSettingsStartFromTheDataSource() {
        assertEquals(
            ThemeSettings(AppTheme.SYSTEM, amoledBlack = false, AccentColor.RED, contrastingSurfaces = null),
            repository.themeSettings.value,
        )

        storeTheme(AppTheme.DARK)
        `when`(dataSource.isAmoledBlack()).thenReturn(true)
        `when`(dataSource.getAccentColor()).thenReturn(AccentColor.TEAL)
        `when`(dataSource.getContrastingSurfaces()).thenReturn(false)

        assertEquals(
            ThemeSettings(AppTheme.DARK, amoledBlack = true, AccentColor.TEAL, contrastingSurfaces = false),
            ThemeRepositoryImpl(dataSource, uiModeManager, applicationScope).themeSettings.value,
        )
    }

    @Test
    fun themeSettingsFollowTheDataSourceChanges() {
        storeTheme(AppTheme.DARK)
        `when`(dataSource.getAccentColor()).thenReturn(AccentColor.TEAL)
        assertEquals(AppTheme.SYSTEM, repository.themeSettings.value.theme)

        themeSettingsChanges.tryEmit(Unit)

        assertEquals(AppTheme.DARK, repository.themeSettings.value.theme)
        assertEquals(AccentColor.TEAL, repository.themeSettings.value.accentColor)
    }

    @Test
    fun gettersReadTheDataSource() {
        storeTheme(AppTheme.DARK)
        `when`(dataSource.getAccentColor()).thenReturn(AccentColor.TEAL)

        assertEquals(AppTheme.DARK, repository.getSelectedTheme())
        assertEquals(AccentColor.TEAL, repository.getAccentColor())
    }

    @Test
    fun settersWriteToTheDataSource() {
        repository.setSelectedTheme(AppTheme.DARK)
        repository.setAccentColor(AccentColor.TEAL)
        repository.setContrastingSurfacesEnabled(false)

        verify(dataSource).setTheme(AppTheme.DARK)
        verify(dataSource).setAccentColor(AccentColor.TEAL)
        verify(dataSource).setContrastingSurfaces(false)
    }

    @Test
    fun contrastingSurfacesFollowTheDarkThemeUntilSet() {
        assertTrue(repository.areContrastingSurfacesEnabled(true))
        assertFalse(repository.areContrastingSurfacesEnabled(false))

        `when`(dataSource.getContrastingSurfaces()).thenReturn(true)
        assertTrue(repository.areContrastingSurfacesEnabled(false))

        `when`(dataSource.getContrastingSurfaces()).thenReturn(false)
        assertFalse(repository.areContrastingSurfacesEnabled(true))
    }

    @Test
    fun allThemesAreAvailable() {
        assertEquals(AppTheme.entries, repository.getAvailableThemes())
    }

    @Test
    fun dynamicColorsFollowTheSelectedTheme() {
        assertFalse(repository.areDynamicColorsEnabled())

        storeTheme(AppTheme.DYNAMIC)
        assertTrue(repository.areDynamicColorsEnabled())
    }

    @Test
    fun applicationNightModeIsNotSetWhenUnsupported() {
        `when`(dataSource.supportsApplicationNightMode()).thenReturn(false)
        storeTheme(AppTheme.DARK)

        repository.updateApplicationNightMode()

        verifyNoInteractions(uiModeManager)
    }

    @Test
    fun applicationNightModeFollowsTheSelectedTheme() {
        repository.updateApplicationNightMode()
        verify(uiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_AUTO)

        storeTheme(AppTheme.DARK)
        repository.updateApplicationNightMode()
        verify(uiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)

        storeTheme(AppTheme.LIGHT)
        repository.updateApplicationNightMode()
        verify(uiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
    }

    @Test
    fun themeChangesUpdateTheNightModeOnlyWhileRegistered() {
        repository.register()
        repository.register()
        storeTheme(AppTheme.DARK)
        themeChanges.tryEmit(Unit)
        // Registering twice still updates once
        verify(uiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)

        repository.unregister()
        storeTheme(AppTheme.LIGHT)
        themeChanges.tryEmit(Unit)
        verify(uiModeManager, never()).setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
    }

    @Test
    fun registerCanBeCalledAgainAfterUnregister() {
        repository.register()
        repository.unregister()
        repository.register()

        storeTheme(AppTheme.DARK)
        themeChanges.tryEmit(Unit)

        verify(uiModeManager).setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
        repository.unregister()
    }

    @Test
    fun unregisterWithoutRegisterDoesNothing() {
        repository.unregister()
        storeTheme(AppTheme.DARK)
        themeChanges.tryEmit(Unit)

        verifyNoInteractions(uiModeManager)
    }
}
