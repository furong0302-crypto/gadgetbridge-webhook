package nodomain.freeyourgadget.gadgetbridge.activities.welcome

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.AppTheme
import nodomain.freeyourgadget.gadgetbridge.util.ui.data.ThemeRepository
import javax.inject.Inject

data class ThemeOption(
    val theme: AppTheme,
    @StringRes val labelRes: Int,
)

data class WelcomeIntroUiState(
    val themes: List<ThemeOption>,
    val selectedTheme: ThemeOption,
)

@HiltViewModel
class WelcomeIntroViewModel @Inject constructor(
    private val repository: ThemeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<WelcomeIntroUiState> = _uiState.asStateFlow()

    private var pendingTheme: AppTheme? = null
    private var pendingSave: Job? = null

    /**
     * Shows [option] as selected at once, but only stores it after [THEME_SAVE_DELAY_MILLIS]. Storing the theme
     * recreates the activity, and doing that while the dropdown is closing would reopen it.
     */
    fun onThemeSelected(option: ThemeOption) {
        _uiState.update { it.copy(selectedTheme = option) }
        pendingTheme = option.theme
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(THEME_SAVE_DELAY_MILLIS)
            savePendingTheme()
        }
    }

    override fun onCleared() {
        // The delayed save was cancelled with the scope, so the selection must not be lost
        savePendingTheme()
    }

    private fun savePendingTheme() {
        pendingTheme?.let { repository.setSelectedTheme(it) }
        pendingTheme = null
    }

    private fun initialState(): WelcomeIntroUiState {
        val selectedTheme = repository.getSelectedTheme()
        return WelcomeIntroUiState(
            themes = repository.getAvailableThemes().map { ThemeOption(it, it.labelRes()) },
            selectedTheme = ThemeOption(selectedTheme, selectedTheme.labelRes()),
        )
    }

    @StringRes
    private fun AppTheme.labelRes(): Int = when (this) {
        AppTheme.SYSTEM -> R.string.pref_theme_system
        AppTheme.LIGHT -> R.string.pref_theme_light
        AppTheme.DARK -> R.string.pref_theme_dark
        AppTheme.DYNAMIC -> R.string.pref_theme_dynamic
    }

    companion object {
        const val THEME_SAVE_DELAY_MILLIS = 500L
    }
}
