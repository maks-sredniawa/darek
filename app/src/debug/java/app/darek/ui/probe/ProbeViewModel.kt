package app.darek.ui.probe

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.darek.MainApplication
import app.darek.listen.Command
import app.darek.listen.Grammar
import app.darek.listen.Heard
import app.darek.listen.LooseMatch
import app.darek.listen.Persona
import app.darek.listen.Recogniser
import app.darek.listen.RecogniserModel
import app.darek.listen.Roast
import app.darek.listen.commandDecoys
import app.darek.listen.commandIn
import app.darek.speak.ReplyPlayer
import app.darek.speak.Voice
import app.darek.speak.VoiceVolume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.vosk.Model
import kotlin.math.abs

enum class ProbePhase { IDLE, PREPARING, LISTENING }

data class ProbeLine(
    val tokens: String,
    val fired: String?,
)

data class ProbeState(
    val phase: ProbePhase = ProbePhase.IDLE,
    val free: Boolean = false,
    val reading: Boolean = false,
    val partial: String = "",
    val finals: List<ProbeLine> = emptyList(),
    val error: String? = null,
)

/**
 * Phase 1's R1/R2 apparatus: every final result goes to `adb logcat -s DarekProbe`, and a matched
 * Command speaks its Reply unless Reading mode is on.
 */
class ProbeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ProbeState())
    val state: StateFlow<ProbeState> = mutableState.asStateFlow()

    @Volatile private var model: Model? = null

    @Volatile private var voice: Voice? = null

    @Volatile private var replies: Map<Command, ShortArray>? = null

    private val fuchyOn = getApplication<MainApplication>().container.fuchyOn
    private val commands = fuchyOn.flatMap { it.commands }

    @Volatile private var persona: Persona = Persona.DAREK
    private var job: Job? = null

    fun setFree(free: Boolean) {
        if (state.value.phase == ProbePhase.IDLE) mutableState.update { it.copy(free = free) }
    }

    fun setReading(reading: Boolean) {
        if (state.value.phase == ProbePhase.IDLE) mutableState.update { it.copy(reading = reading) }
    }

    fun start() {
        if (job != null) return
        if (ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        job =
            viewModelScope.launch {
                mutableState.update { it.copy(phase = ProbePhase.PREPARING, error = null) }
                try {
                    val chosen =
                        getApplication<MainApplication>()
                            .container.preferences.persona
                            .first()
                    if (chosen != persona) {
                        voice?.close()
                        voice = null
                        replies = null
                        persona = chosen
                    }
                    val loaded = withContext(Dispatchers.IO) { model ?: load().also { model = it } }
                    val free = state.value.free
                    val reading = state.value.reading
                    if (!reading) withContext(Dispatchers.Default) { if (replies == null) replies = renderReplies() }
                    val mode = if (free) "free" else "grammar"
                    mutableState.update { it.copy(phase = ProbePhase.LISTENING) }
                    Log.i(TAG, "start mode=$mode reading=$reading")
                    val grammar =
                        when {
                            free -> null
                            reading -> Grammar.json(commands, commandDecoys(fuchyOn), persona)
                            else ->
                                Json.encodeToString(
                                    (
                                        Grammar.phrases(
                                            commands,
                                            commandDecoys(fuchyOn),
                                            persona,
                                        ) + Roast.phrases(persona)
                                    ).distinct(),
                                )
                        }
                    Recogniser(loaded).heard(grammar).collect { heard ->
                        when (heard) {
                            is Heard.Partial ->
                                mutableState.update {
                                    it.copy(
                                        partial = heard.tokens.joinToString(" "),
                                    )
                                }
                            is Heard.Final -> onFinal(heard, mode, reading)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "failed", e)
                    mutableState.update { it.copy(error = e.toString()) }
                } finally {
                    Log.i(TAG, "stop")
                    mutableState.update { it.copy(phase = ProbePhase.IDLE, partial = "") }
                    job = null
                }
            }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun onFinal(
        heard: Heard.Final,
        mode: String,
        reading: Boolean,
    ) {
        if (heard.tokens.isEmpty()) return
        val tokens = heard.tokens
        val exact = commandIn(tokens, commands, persona)
        val likeness = LooseMatch.likeness(getApplication())
        val command =
            when {
                exact != null || reading -> exact
                else ->
                    commands.firstOrNull {
                        !it.calls &&
                            LooseMatch.matches(tokens, it.words(persona), likeness)
                    }
            }
        val roasted = if (command == null && !reading) Roast.nameIn(tokens, likeness, persona) else null
        val fired = command?.label ?: roasted?.let { "ROAST ${it.vocative}" }
        val match =
            when {
                fired == null -> "-"
                exact != null || (roasted != null && Roast.nameIn(tokens, EXACT, persona) != null) -> "exact"
                else -> "loose"
            }
        val line = ProbeLine(tokens.joinToString(" "), fired?.let { if (match == "loose") "$it ~loose" else it })
        Log.i(TAG, "final mode=$mode command=${fired ?: "-"} match=$match tokens=\"${line.tokens}\"")
        mutableState.update { it.copy(partial = "", finals = (listOf(line) + it.finals).take(MAX_LINES)) }
        if (reading) return
        val reply =
            when {
                command != null -> replies?.get(command)
                roasted != null ->
                    voice?.let { v ->
                        withContext(Dispatchers.Default) { v.synthesize(Roast.insult(roasted)) }
                    }
                else -> null
            } ?: return
        val end = ReplyPlayer.play(reply, VoiceVolume.get(getApplication()))
        Log.i(TAG, "reply command=$fired end=$end")
    }

    // Recognition goes on while he speaks; neither Reply holds a Command's words, so he cannot answer himself.
    private fun renderReplies(): Map<Command, ShortArray> =
        runCatching {
            val started = SystemClock.elapsedRealtime()
            val loaded = voice ?: Voice.load(getApplication(), persona.voice, persona.voiceScales).also { voice = it }
            commands
                .associateWith { loaded.synthesize(it.reply.by(persona)) }
                .also { rendered ->
                    val peaks =
                        rendered.entries.joinToString(
                            " ",
                        ) { (c, pcm) -> "${c.label}=${pcm.maxOf { abs(it.toInt()) }}" }
                    Log.i(TAG, "voice ready=${SystemClock.elapsedRealtime() - started}ms peak $peaks")
                }
        }.getOrElse {
            Log.e(TAG, "voice failed", it)
            emptyMap()
        }

    private fun load(): Model {
        val started = SystemClock.elapsedRealtime()
        val directory = RecogniserModel.unpack(getApplication())
        val unpacked = SystemClock.elapsedRealtime()
        return Model(directory.path).also {
            Log.i(TAG, "model unpack=${unpacked - started}ms load=${SystemClock.elapsedRealtime() - unpacked}ms")
        }
    }

    // A cancelled job's blocking load still runs to the end, so the model may not exist yet.
    override fun onCleared() {
        val release = {
            model?.close()
            voice?.close()
        }
        job?.invokeOnCompletion { release() } ?: release()
    }

    private companion object {
        const val TAG = "DarekProbe"
        const val MAX_LINES = 100
        const val EXACT = 1f
    }
}
