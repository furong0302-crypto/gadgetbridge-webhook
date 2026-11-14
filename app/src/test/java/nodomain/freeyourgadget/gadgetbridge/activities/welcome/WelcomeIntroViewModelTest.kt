package nodomain.freeyourgadget.gadgetbridge.activities.welcome

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.test.MainDispatcherRule
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AppTheme
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24], application = Application::class)
class WelcomeIntroViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mock(ThemeRepository::class.java)

    @Before
    fun setUp() {
        `when`(repository.getAvailableThemes()).thenReturn(AppTheme.entries)
        `when`(repository.getSelectedTheme()).thenReturn(AppTheme.DARK)
    }

    private fun WelcomeIntroViewModel.option(theme: AppTheme) = uiState.value.themes.first { it.theme == theme }

    @Test
    fun uiStateHasTheAvailableAndSelectedThemes() {
        val uiState = WelcomeIntroViewModel(repository).uiState.value

        assertEquals(AppTheme.entries, uiState.themes.map { it.theme })
        assertEquals(ThemeOption(AppTheme.DARK, R.string.pref_theme_dark), uiState.selectedTheme)
    }

    @Test
    fun labelsMatchThePreferenceEntries() {
        val context = RuntimeEnvironment.getApplication()

        val labels = WelcomeIntroViewModel(repository).uiState.value.themes.map { context.getString(it.labelRes) }

        assertEquals(context.resources.getStringArray(R.array.pref_theme_options).toList(), labels)
    }

    @Test
    fun selectingAThemeUpdatesTheUiStateAtOnceAndStoresItAfterTheDelay() = runTest {
        val viewModel = WelcomeIntroViewModel(repository)
        val light = viewModel.option(AppTheme.LIGHT)

        viewModel.onThemeSelected(light)
        assertEquals(light, viewModel.uiState.value.selectedTheme)

        advanceTimeBy(WelcomeIntroViewModel.THEME_SAVE_DELAY_MILLIS - 1)
        verify(repository, never()).setSelectedTheme(AppTheme.LIGHT)

        advanceTimeBy(1)
        runCurrent()
        verify(repository).setSelectedTheme(AppTheme.LIGHT)
    }

    @Test
    fun onlyTheLastOfQuickSelectionsIsStored() = runTest {
        val viewModel = WelcomeIntroViewModel(repository)

        viewModel.onThemeSelected(viewModel.option(AppTheme.LIGHT))
        viewModel.onThemeSelected(viewModel.option(AppTheme.SYSTEM))
        advanceTimeBy(WelcomeIntroViewModel.THEME_SAVE_DELAY_MILLIS)
        runCurrent()

        verify(repository, never()).setSelectedTheme(AppTheme.LIGHT)
        verify(repository).setSelectedTheme(AppTheme.SYSTEM)
    }

    @Test
    fun aPendingThemeIsStoredWhenTheViewModelIsCleared() = runTest {
        val store = ViewModelStore()
        val viewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WelcomeIntroViewModel(repository) as T
        })[WelcomeIntroViewModel::class.java]

        viewModel.onThemeSelected(viewModel.option(AppTheme.LIGHT))
        store.clear()
        advanceTimeBy(WelcomeIntroViewModel.THEME_SAVE_DELAY_MILLIS)
        runCurrent()

        // Stored once on clear, and not again by the cancelled delayed save
        verify(repository, times(1)).setSelectedTheme(AppTheme.LIGHT)
    }
}
