package app.darek.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.data.AppPreferences
import app.darek.data.ThemeMode
import app.darek.listen.Persona
import app.darek.theme.applyThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val materialYou: Boolean = false,
    val persona: Persona = Persona.DAREK,
    val autoUpdate: Boolean = true,
    val showProfanity: Boolean = false,
)

class SettingsViewModel(
    private val preferences: AppPreferences,
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(
            preferences.themeMode,
            preferences.materialYou,
            preferences.persona,
            preferences.autoUpdate,
            preferences.showProfanity,
            ::SettingsUiState,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /**
     * **Both halves, and in this order.**
     *
     * [applyThemeMode] moves the *window* — the background painted before Compose composes, and the
     * system-bar scrim — which Compose cannot reach. Writing the preference alone repaints the app
     * and leaves the window following the phone. Applying it alone repaints the window and forgets
     * the choice on the next cold start. See `theme/NightMode.kt`.
     */
    fun setThemeMode(mode: ThemeMode) {
        applyThemeMode(mode)
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setPersona(persona: Persona) {
        viewModelScope.launch { preferences.setPersona(persona) }
    }

    fun setMaterialYou(enabled: Boolean) {
        viewModelScope.launch { preferences.setMaterialYou(enabled) }
    }

    fun setAutoUpdate(auto: Boolean) {
        viewModelScope.launch { preferences.setAutoUpdate(auto) }
    }

    fun setShowProfanity(show: Boolean) {
        viewModelScope.launch { preferences.setShowProfanity(show) }
    }

    /**
     * Every setting back to how the app came, then the two halves the store does not hold: the
     * theme's window half, as in [setThemeMode], and the language, which the platform keeps
     * ([setAppLanguage]) and which goes back to Polish, as on a first launch (ADR-0012). Language
     * last, because it recreates the Activity.
     */
    fun restoreDefaults() {
        viewModelScope.launch {
            preferences.restoreDefaults()
            applyThemeMode(preferences.themeModeNow())
            setAppLanguage(AppLanguage.POLISH)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    SettingsViewModel(app.preferences)
                }
            }
    }
}
