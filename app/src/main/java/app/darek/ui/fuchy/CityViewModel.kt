package app.darek.ui.fuchy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.fuchy.City
import app.darek.fuchy.Fucha
import app.darek.fuchy.Fuchy
import app.darek.weather.CitySearch
import app.darek.weather.Weather
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CityUiState(
    val fucha: Fucha? = null,
    val current: City? = null,
    val query: String = "",
    val searching: Boolean = false,
    val result: CitySearch? = null,
    val done: Boolean = false,
)

/** *The city you chose* (phase-F.md section 5): searched by name, picked from the answers, stored as coordinates. */
class CityViewModel(
    private val fuchaId: String,
    private val switchOn: Boolean,
    private val fuchy: Fuchy,
    private val weather: Weather,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CityUiState())
    val state: StateFlow<CityUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val installed = fuchy.find(fuchaId)
            mutableState.update {
                it.copy(
                    fucha = installed?.fucha,
                    current = installed?.state?.city,
                    done =
                        installed == null,
                )
            }
        }
    }

    fun type(query: String) = mutableState.update { it.copy(query = query) }

    fun search() {
        val now = mutableState.value
        val fucha = now.fucha ?: return
        if (now.query.isBlank() || now.searching) return
        mutableState.update { it.copy(searching = true) }
        viewModelScope.launch {
            val result = weather.search(fucha, now.query)
            mutableState.update { it.copy(searching = false, result = result) }
        }
    }

    fun pick(city: City) {
        val fucha = mutableState.value.fucha ?: return
        viewModelScope.launch {
            fuchy.setCity(fucha.id, city)
            if (switchOn) fuchy.switchOn(fucha)
            mutableState.update { it.copy(done = true) }
        }
    }

    companion object {
        fun factory(
            fuchaId: String,
            switchOn: Boolean,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    CityViewModel(fuchaId, switchOn, app.container.fuchy, app.container.weather)
                }
            }
    }
}
