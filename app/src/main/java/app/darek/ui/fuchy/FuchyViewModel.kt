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
import app.darek.fuchy.GrantDefault
import app.darek.fuchy.Installed
import app.darek.fuchy.Permission
import app.darek.fuchy.defaultFor
import app.darek.fuchy.missingGrants
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

    /** Switched on, but a grant it needs was taken back, or never given before grants existed. */
    data class NotGranted(
        val permission: Permission,
    ) : OffBecause

    /** *Uprawnienia fuch* never allows [permission], so switching it on asks nothing and does nothing. */
    data class Never(
        val permission: Permission,
    ) : OffBecause
}

data class FuchaRow(
    val installed: Installed,
    val offBecause: OffBecause?,
)

/** A switch-on waiting for the user's grant (phase-F.md section 5). */
data class GrantAsk(
    val fucha: Fucha,
    val permissions: List<Permission>,
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
    val grantAsk: GrantAsk? = null,
    val removing: Fucha? = null,
)

sealed interface FuchyEvent {
    /** One fucha switched off because it can never be on beside the one switched on, from here or from *Twoje miasto*. */
    data class SwitchedOff(
        val off: Fucha,
        val by: Fucha,
    ) : FuchyEvent

    /** *Uprawnienia fuch* says never for [permission], so nothing is asked. */
    data class Blocked(
        val fucha: Fucha,
        val permission: Permission,
    ) : FuchyEvent

    /** Granted, it now needs its city before it can be on. */
    data class PickCity(
        val fucha: Fucha,
    ) : FuchyEvent
}

class FuchyViewModel(
    private val fuchy: Fuchy,
    persona: Flow<Persona>,
) : ViewModel() {
    private val dialogs = MutableStateFlow(FuchyUiState())
    private val channel = Channel<FuchyEvent>(Channel.BUFFERED)
    val events: Flow<FuchyEvent> = channel.receiveAsFlow()

    init {
        viewModelScope.launch { fuchy.switchedOff.collect { channel.send(FuchyEvent.SwitchedOff(it.off, it.by)) } }
    }

    val state: StateFlow<FuchyUiState> =
        combine(fuchy.installed, fuchy.grantDefaults, persona, dialogs) { all, defaults, persona, dialogs ->
            val rows = all.map { FuchaRow(it, if (it.on) null else offBecause(it, all, defaults)) }
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
                afterClashes(fucha)
            } else {
                dialogs.value =
                    dialogs.value.copy(override = Override(fucha, plan.warnings))
            }
        }
    }

    fun override() {
        val fucha = dialogs.value.override?.fucha ?: return
        dialogs.value = dialogs.value.copy(override = null)
        viewModelScope.launch { afterClashes(fucha) }
    }

    fun grant() {
        val ask = dialogs.value.grantAsk ?: return
        dialogs.value = dialogs.value.copy(grantAsk = null)
        viewModelScope.launch {
            fuchy.grant(ask.fucha.id, ask.permissions)
            afterClashes(ask.fucha)
        }
    }

    /** Refused, the fucha stays off and no city is asked, so nothing goes out ungranted. */
    fun dismissGrant() {
        dialogs.value = dialogs.value.copy(grantAsk = null)
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

    /** The clashes are settled: then the grant, before the city, then the city (phase-F.md section 5). */
    private suspend fun afterClashes(fucha: Fucha) {
        val state = fuchy.find(fucha.id)?.state
        val defaults = fuchy.grantDefaults.first()
        val missing = missingGrants(fucha, state, defaults)
        missing.firstOrNull { defaults.defaultFor(it) == GrantDefault.NEVER }?.let {
            return channel.send(FuchyEvent.Blocked(fucha, it))
        }
        when {
            missing.isNotEmpty() -> dialogs.value = dialogs.value.copy(grantAsk = GrantAsk(fucha, missing))
            fucha.needsCity && state?.city == null -> channel.send(FuchyEvent.PickCity(fucha))
            else -> fuchy.switchOn(fucha)
        }
    }

    private fun offBecause(
        installed: Installed,
        all: List<Installed>,
        defaults: Map<Permission, GrantDefault>,
    ): OffBecause? {
        val fucha = installed.fucha
        val missing = missingGrants(fucha, installed.state, defaults)
        if (installed.state?.on == true) missing.firstOrNull()?.let { return OffBecause.NotGranted(it) }
        missing.firstOrNull { defaults.defaultFor(it) == GrantDefault.NEVER }?.let { return OffBecause.Never(it) }
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
