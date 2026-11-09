package app.darek.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.data.AppPreferences
import app.darek.listen.GateNumber
import app.darek.listen.Listening
import app.darek.listen.Persona
import app.darek.listen.enabledCommands
import app.darek.tip.TipJar
import app.darek.tip.tipAskDue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class HomeUiState(
    /** False wherever Play will not sell a tip: nothing is asked for that cannot be bought (ADR-0007). */
    val showTipAsk: Boolean = false,
    val listening: Listening.Phase = Listening.Phase.OFF,
    val lastReply: String? = null,
    val listenFailed: Boolean = false,
    val hearingName: Boolean = false,
    val persona: Persona = Persona.DAREK,
    /** The system will not show its dialog again, so Home sends the user to the app's settings. */
    val micDeniedForGood: Boolean = false,
)

class HomeViewModel(
    private val preferences: AppPreferences,
    private val tipJar: TipJar,
    private val listening: Listening,
    private val installedAt: Instant,
    private val gateNumber: () -> String?,
) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(
            preferences.tipAnsweredUnder,
            tipJar.offers,
            listening.state,
            preferences.micDeniedUnder,
            preferences.persona,
        ) { answeredUnder, offers, heard, deniedUnder, persona ->
            HomeUiState(
                showTipAsk = offers.isNotEmpty() && tipAskDue(answeredUnder, installedAt, Instant.now()),
                listening = heard.phase,
                lastReply = heard.lastReply,
                listenFailed = heard.failed,
                hearingName = heard.hearingName,
                persona = persona,
                micDeniedForGood = deniedUnder == installedAt,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun listen() {
        viewModelScope.launch { listening.start(enabledCommands(gateNumber()), preferences.persona.first()) }
    }

    fun stopListening() = listening.stop()

    /**
     * The microphone answer, judged the only way Android allows: a denial is for good when the
     * rationale was due before the request and is not after it. A first dialog dismissed with Back
     * also leaves it false, which is why the before-value is needed.
     */
    fun onMicAnswer(
        granted: Boolean,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
    ) {
        if (granted) {
            listen()
        } else if (rationaleBefore && !rationaleAfter) {
            viewModelScope.launch { preferences.setMicDenied(installedAt) }
        }
    }

    /** Re-read on every `ON_START`: a grant given in the app's settings clears the stored denial. */
    fun onMicGrantRead(granted: Boolean) {
        if (granted) viewModelScope.launch { preferences.setMicDenied(null) }
    }

    init {
        viewModelScope.launch { preferences.persona.distinctUntilChanged().collect { listening.switchTo(it) } }
        // Prices are asked for only once the ask could be due, which saves a product query on every
        // other visit. The binding itself happens on every foreground anyway, to settle tips.
        viewModelScope.launch {
            if (tipAskDue(preferences.tipAnsweredUnder.first(), installedAt, Instant.now())) tipJar.refresh()
        }
    }

    /** Either answer, *Leave a tip* or *No thanks*, ends the ask on this install. */
    fun answerTipAsk() {
        viewModelScope.launch { preferences.markTipAnswered(installedAt) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    HomeViewModel(
                        app.container.preferences,
                        app.container.tipJar,
                        app.container.listening,
                        app.container.installedAt,
                        { GateNumber.get(app) },
                    )
                }
            }
    }
}
