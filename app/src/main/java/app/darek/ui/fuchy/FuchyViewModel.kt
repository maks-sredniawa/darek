package app.darek.ui.fuchy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.fuchy.Clash
import app.darek.fuchy.Fucha
import app.darek.fuchy.Fuchy
import app.darek.fuchy.Installed
import app.darek.fuchy.switchingOn
import app.darek.listen.Persona
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Why an installed fucha is off, read from what switching it on would do rather than remembered. */
sealed interface OffBecause {
    data class Holds(
        val other: Fucha,
    ) : OffBecause

    data class SoundsLike(
        val other: Fucha,
    ) : OffBecause

    data object NearName : OffBecause
}

data class FuchaRow(
    val installed: Installed,
    val offBecause: OffBecause?,
)

/** A switch-on waiting for the user to override its clashes (ADR-0015, *Phrases that clash*). */
data class Override(
    val fucha: Fucha,
    val warnings: List<Clash>,
)

data class FuchyUiState(
    val loaded: Boolean = false,
    val own: FuchaRow? = null,
    val installed: List<FuchaRow> = emptyList(),
    val persona: Persona = Persona.DAREK,
    val override: Override? = null,
    val removing: Fucha? = null,
)

/** One fucha switched off because it can never be on beside the one switched on. */
data class SwitchedOff(
    val off: Fucha,
    val by: Fucha,
)

class FuchyViewModel(
    private val fuchy: Fuchy,
    persona: Flow<Persona>,
) : ViewModel() {
    private val dialogs = MutableStateFlow(FuchyUiState())
    private val switchedOff = Channel<SwitchedOff>(Channel.BUFFERED)
    val events: Flow<SwitchedOff> = switchedOff.receiveAsFlow()

    val state: StateFlow<FuchyUiState> =
        combine(fuchy.installed, persona, dialogs) { all, persona, dialogs ->
            val rows = all.map { FuchaRow(it, if (it.on) null else offBecause(it.fucha, all)) }
            dialogs.copy(
                loaded = true,
                own = rows.firstOrNull { it.installed.fucha.bundled },
                installed = rows.filterNot { it.installed.fucha.bundled }.sortedBy { it.installed.fucha.name },
                persona = persona,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuchyUiState())

    fun switch(
        fucha: Fucha,
        on: Boolean,
    ) {
        viewModelScope.launch {
            if (!on) return@launch fuchy.switch(mapOf(fucha.id to false))
            val plan = switchingOn(fucha, fuchy.installed.first())
            if (plan.warnings.isEmpty()) {
                switchOn(fucha)
            } else {
                dialogs.value =
                    dialogs.value.copy(override = Override(fucha, plan.warnings))
            }
        }
    }

    fun override() {
        val fucha = dialogs.value.override?.fucha ?: return
        dialogs.value = dialogs.value.copy(override = null)
        viewModelScope.launch { switchOn(fucha) }
    }

    fun dismissOverride() {
        dialogs.value = dialogs.value.copy(override = null)
    }

    fun askRemove(fucha: Fucha) {
        dialogs.value = dialogs.value.copy(removing = fucha)
    }

    fun dismissRemove() {
        dialogs.value = dialogs.value.copy(removing = null)
    }

    fun remove() {
        val fucha = dialogs.value.removing ?: return
        dialogs.value = dialogs.value.copy(removing = null)
        viewModelScope.launch { fuchy.remove(fucha.id) }
    }

    private suspend fun switchOn(fucha: Fucha) {
        val turnsOff = switchingOn(fucha, fuchy.installed.first()).turnsOff
        fuchy.switch(mapOf(fucha.id to true) + turnsOff.associate { it.id to false })
        turnsOff.forEach { switchedOff.send(SwitchedOff(it, fucha)) }
    }

    private fun offBecause(
        fucha: Fucha,
        all: List<Installed>,
    ): OffBecause? {
        val plan = switchingOn(fucha, all)
        plan.turnsOff.firstOrNull()?.let { return OffBecause.Holds(it) }
        val warning = plan.warnings.firstOrNull() ?: return null
        val other =
            when (warning) {
                is Clash.SoundsLike -> warning.enabled.fucha
                is Clash.NearWord -> warning.enabled.fucha
                is Clash.Holds, is Clash.NearName -> null
            }
        return other?.let { id -> all.firstOrNull { it.fucha.id == id }?.fucha?.let(OffBecause::SoundsLike) }
            ?: OffBecause.NearName.takeIf { warning is Clash.NearName }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    FuchyViewModel(app.container.fuchy, app.preferences.persona)
                }
            }
    }
}
