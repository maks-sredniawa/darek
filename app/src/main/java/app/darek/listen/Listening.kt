package app.darek.listen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import app.darek.speak.LoadedLine
import app.darek.speak.PlaybackEnd
import app.darek.speak.ReplyPlayer
import app.darek.speak.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.util.concurrent.ConcurrentHashMap

/**
 * Listening (`CONTEXT.md`): the microphone open and the enabled Commands able to fire. Held by
 * `AppContainer`, so the model and the voice stay loaded between starts; [ListeningService] keeps it
 * running with the screen off (ADR-0010).
 *
 * Each Command ends the recording before Darek speaks and starts a new one when his Reply is
 * finished, so he never hears a Reply. Only *„No?"*, the answer to his name, plays with the microphone
 * open, since it comes while the Command is still being said (ADR-0011).
 */
class Listening(
    context: Context,
    scope: CoroutineScope,
    private val volume: () -> Float,
) {
    enum class Phase { OFF, PREPARING, ON }

    data class State(
        val phase: Phase = Phase.OFF,
        val lastReply: String? = null,
        val failed: Boolean = false,
        /** A partial result holds the Persona's name: it shows it is hearing before the Command is finished. */
        val hearingName: Boolean = false,
        val hearing: Hearing = Hearing.HEARS,
    )

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState.asStateFlow()

    @Volatile private var model: Model? = null

    @Volatile private var voice: Pair<Persona, Voice>? = null

    private val rendered = ConcurrentHashMap<Pair<Persona, String>, ShortArray>()

    @Volatile private var lastNotUnderstood: Line? = null

    @Volatile private var cancelWindow: CancelWindow? = null

    private data class Wanted(
        val enabled: Set<Command>,
        val persona: Persona,
    )

    private val runs =
        LatestRun<Wanted>(scope, { listen(it.enabled, it.persona) }) {
            mutableState.update { it.copy(phase = Phase.OFF, hearingName = false, hearing = Hearing.HEARS) }
        }

    private data class Answer(
        val label: String,
        val text: String,
        val calls: Boolean = false,
    )

    /** True from [start] until Listening is stopped or fails. */
    val running: Flow<Boolean> get() = runs.requested

    /** Started again under another Persona, a running Listening restarts (ADR-0016). */
    fun start(
        enabled: Set<Command>,
        persona: Persona,
    ) {
        if (enabled.isEmpty()) return
        runs.run(Wanted(enabled, persona))
    }

    /** A running Listening restarts under [persona]; a stopped one stays stopped (ADR-0016). */
    fun switchTo(persona: Persona) = runs.change { it.copy(persona = persona) }

    fun stop() {
        if (cancelWindow?.listeningStopped() == CancelWindow.Decision.STOPPED) {
            Log.i(TAG, "cancel window=${CancelWindow.Decision.STOPPED}")
        }
        runs.stop()
    }

    private suspend fun listen(
        enabled: Set<Command>,
        persona: Persona,
    ) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        mutableState.update { it.copy(phase = Phase.PREPARING, failed = false) }
        var nameAnswer: LoadedLine? = null
        try {
            coroutineScope {
                val loaded = withContext(Dispatchers.IO) { model ?: loadModel().also { model = it } }
                withContext(Dispatchers.Default) { prepareVoice(persona) }
                withContext(Dispatchers.Default) { renderAhead(persona, listOf(NAME_HEARD)) }
                // An answer that cannot load or play costs the hint, never Listening.
                nameAnswer =
                    rendered[persona to NAME_HEARD.by(persona)]?.let { samples ->
                        runCatching { LoadedLine(samples) }
                            .onFailure { Log.e(TAG, "name answer failed to load", it) }
                            .getOrNull()
                    }
                if (enabled.any { it.calls }) {
                    withContext(Dispatchers.Default) {
                        renderAhead(persona, NOT_UNDERSTOOD.take(1) + CALL_CANCELLED + CALL_FAILED)
                    }
                    launch(Dispatchers.Default) { renderAhead(persona, NOT_UNDERSTOOD.drop(1)) }
                }
                val grammar = Grammar.json(enabled, persona, ListeningExtras.phrases(persona))
                mutableState.update { it.copy(phase = Phase.ON) }
                while (true) {
                    pauseWhileInCall()
                    val nameHeard = NameHeard(persona.word)
                    val answer =
                        merge(
                            Recogniser(loaded)
                                .heard(grammar, holdAfter = persona.word, onHears = ::hears)
                                .onEach { heard ->
                                    val hearing = heard is Heard.Partial && persona.word in heard.tokens
                                    mutableState.update { it.copy(hearingName = hearing) }
                                    if (nameHeard.isFirst(heard)) answerName(nameAnswer)
                                }.filterIsInstance<Heard.Final>()
                                .mapNotNull { answerTo(it.tokens, enabled, persona) },
                            callStarted(),
                        ).first()
                    if (answer == null) continue
                    Log.i(TAG, "reaction=${answer.label}")
                    mutableState.update { it.copy(hearingName = false, lastReply = answer.text) }
                    if (answer.calls) {
                        callPlaced(loaded, grammar, persona, answer)
                    } else {
                        Log.i(VOICE_TAG, "reply reaction=${answer.label} end=${speak(persona, answer.text)}")
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "listening failed", e)
            mutableState.update { it.copy(failed = true) }
        } finally {
            nameAnswer?.close()
        }
    }

    private fun inCall(): Boolean = inCall(audio.mode)

    /** The microphone is freed for the call and taken back when the call ends (phase-2.md, section 3). */
    private suspend fun pauseWhileInCall() {
        if (inCall()) {
            Log.i(TAG, "paused for a call")
            mutableState.update { it.copy(hearing = Hearing.PAUSED_FOR_CALL, hearingName = false) }
            while (inCall()) delay(CALL_POLL_MS)
            Log.i(TAG, "call ended, listening again")
        }
        mutableState.update { it.copy(hearing = Hearing.HEARS) }
    }

    private fun callStarted(): Flow<Answer?> =
        flow {
            while (!inCall()) delay(CALL_POLL_MS)
            emit(null)
        }

    private fun hears(hears: Boolean) {
        Log.i(TAG, "microphone hears=$hears")
        mutableState.update {
            if (it.hearing ==
                Hearing.PAUSED_FOR_CALL
            ) {
                it
            } else {
                it.copy(hearing = if (hears) Hearing.HEARS else Hearing.CANNOT_HEAR)
            }
        }
    }

    private fun answerName(line: LoadedLine?) {
        val outcome =
            when {
                line == null -> "none"
                else ->
                    runCatching { line.play(volume()) }.fold(
                        onSuccess = { "played" },
                        onFailure = {
                            Log.e(TAG, "name answer failed to play", it)
                            "failed"
                        },
                    )
            }
        Log.i(TAG, "name heard answer=$outcome")
    }

    private fun answerTo(
        tokens: List<String>,
        enabled: Set<Command>,
        persona: Persona,
    ): Answer? {
        val reaction = reactionTo(tokens, enabled, persona)
        if (reaction is Reaction.Fires) {
            return Answer(reaction.command.name, reaction.command.reply.by(persona), reaction.command.calls)
        }
        ListeningExtras.reply(appContext, tokens, persona)?.let { return Answer("EXTRA", it) }
        return when (reaction) {
            null, is Reaction.Fires -> null
            Reaction.Corrected -> Answer("CORRECTED", persona.correction)
            Reaction.NotUnderstood -> Answer("NOT_UNDERSTOOD", pickNotUnderstood(persona))
            Reaction.NotConfigured -> Answer("NOT_CONFIGURED", NOT_CONFIGURED.by(persona))
        }
    }

    private fun pickNotUnderstood(persona: Persona): String {
        val ready =
            NOT_UNDERSTOOD
                .filter { rendered.containsKey(persona to it.by(persona)) }
                .ifEmpty { NOT_UNDERSTOOD.take(1) }
        return ready
            .filter { it != lastNotUnderstood }
            .ifEmpty { ready }
            .random()
            .also { lastNotUnderstood = it }
            .by(persona)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun callPlaced(
        model: Model,
        grammar: String,
        persona: Persona,
        answer: Answer,
    ) {
        val window = CancelWindow().also { cancelWindow = it }
        val decision =
            try {
                Log.i(VOICE_TAG, "reply reaction=${answer.label} end=${speak(persona, answer.text)}")
                val replyEnded = SystemClock.elapsedRealtime()
                merge(
                    Recogniser(model)
                        .heard(grammar, onRecording = {
                            val now = SystemClock.elapsedRealtime()
                            window.recordingStarted(now)
                            Log.i(TAG, "cancel window open startup=${now - replyEnded}ms")
                        }, onHears = { if (!it) window.cannotHear() })
                        .map { window.heard(it.tokens, SystemClock.elapsedRealtime()) }
                        .catch { e ->
                            if (e is CancellationException) throw e
                            Log.e(TAG, "cancel window could not listen", e)
                            emit(window.cannotHear())
                        },
                    flow {
                        while (true) {
                            delay(WINDOW_TICK_MS)
                            emit(window.tick(SystemClock.elapsedRealtime()))
                        }
                    },
                ).first { it != CancelWindow.Decision.WAIT }
            } finally {
                cancelWindow = null
            }
        Log.i(TAG, "cancel window=$decision")
        when (decision) {
            CancelWindow.Decision.DIAL -> {
                currentCoroutineContext().ensureActive()
                if (!dial()) say(persona, CALL_FAILED)
            }
            CancelWindow.Decision.CANCELLED -> say(persona, CALL_CANCELLED)
            CancelWindow.Decision.CANNOT_HEAR -> say(persona, CALL_FAILED)
            CancelWindow.Decision.STOPPED, CancelWindow.Decision.WAIT -> Unit
        }
    }

    // CALL_PHONE is in the debug manifest only: a release has no Number, and a denied grant is a failed dial.
    @SuppressLint("MissingPermission")
    private fun dial(): Boolean {
        val number = GateNumber.get(appContext) ?: return false
        return runCatching {
            appContext
                .getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", number, null), null)
        }.onFailure { Log.e(TAG, "dial failed", it) }.isSuccess
    }

    private suspend fun say(
        persona: Persona,
        line: Line,
    ) {
        val text = line.by(persona)
        mutableState.update { it.copy(lastReply = text) }
        Log.i(VOICE_TAG, "reply end=${speak(persona, text)}")
    }

    private suspend fun speak(
        persona: Persona,
        text: String,
    ): PlaybackEnd {
        val samples =
            rendered[persona to text]
                ?: voice
                    ?.takeIf { it.first == persona }
                    ?.let { (_, loaded) -> withContext(Dispatchers.Default) { synthesized(loaded, text) } }
                ?: return PlaybackEnd.FAILED
        return ReplyPlayer.play(samples, volume())
    }

    // A voice that fails to load or to synthesize leaves the Replies shown but silent; Listening
    // goes on, and the next start tries again. Only the chosen Persona's voice is held (ADR-0016).
    private suspend fun prepareVoice(persona: Persona) {
        if (voice?.first == persona) return
        voice?.second?.close()
        voice = null
        val started = SystemClock.elapsedRealtime()
        voice =
            runCatching { Voice.load(appContext, persona.voice, persona.voiceScales) }
                .onFailure { Log.e(VOICE_TAG, "voice failed to load", it) }
                .getOrNull()
                ?.let { persona to it } ?: return
        renderAhead(persona, Command.entries.map { it.reply } + NOT_CONFIGURED + Line(persona.correction))
        Log.i(VOICE_TAG, "persona=$persona ready=${SystemClock.elapsedRealtime() - started}ms")
    }

    private suspend fun renderAhead(
        persona: Persona,
        lines: List<Line>,
    ) {
        val (owner, loaded) = voice ?: return
        if (owner != persona) return
        for (line in lines) {
            currentCoroutineContext().ensureActive()
            val key = persona to line.by(persona)
            if (rendered.containsKey(key)) continue
            synthesized(loaded, key.second)?.let { rendered[key] = it }
        }
    }

    private fun synthesized(
        loaded: Voice,
        text: String,
    ): ShortArray? =
        runCatching { loaded.synthesize(text) }
            .onFailure { Log.e(VOICE_TAG, "synthesis failed", it) }
            .getOrNull()

    private fun loadModel(): Model {
        val started = SystemClock.elapsedRealtime()
        val directory = RecogniserModel.unpack(appContext)
        val unpacked = SystemClock.elapsedRealtime()
        return Model(directory.path).also {
            Log.i(MODEL_TAG, "unpack=${unpacked - started}ms load=${SystemClock.elapsedRealtime() - unpacked}ms")
        }
    }

    private companion object {
        const val TAG = "DarekListen"
        const val MODEL_TAG = "DarekModel"
        const val VOICE_TAG = "DarekVoice"
        const val WINDOW_TICK_MS = 50L
        const val CALL_POLL_MS = 500L
    }
}
