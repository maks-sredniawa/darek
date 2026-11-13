package app.darek.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import app.darek.MainApplication
import app.darek.R
import app.darek.listen.GateNumber
import app.darek.listen.LooseMatch
import app.darek.listen.Persona
import app.darek.listen.RecogniserModel
import app.darek.listen.Vocabulary
import app.darek.listen.tokensOf
import app.darek.speak.ReplyPlayer
import app.darek.speak.Voice
import app.darek.speak.VoiceVolume
import app.darek.theme.Spacing
import app.darek.ui.common.SectionHeader
import app.darek.ui.probe.ProbeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import kotlin.math.roundToInt

/**
 * The developer-only section of Settings. **This file exists only in the debug source set** — see
 * the release half for why that is a source set and not a `BuildConfig.DEBUG` branch.
 *
 * *Say this* speaks any text in Darek's voice and logs what phase 1's R3 reads:
 * `adb logcat -s DarekVoice`. The probe is R1/R2's: `adb logcat -s DarekProbe`.
 */
@Composable
fun DebugSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sample by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf(samples[0]) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(VoiceVolume.get(context)) }
    val persona by (context.applicationContext as MainApplication)
        .container.preferences.persona
        .collectAsState(Persona.DAREK)
    var scales by remember(persona) { mutableStateOf<FloatArray?>(null) }

    SectionHeader(stringResource(R.string.debug_voice))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        status =
                            runCatching { DebugVoice.say(context, text, volume, scales) }.getOrElse { "failed: $it" }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.debug_voice_say)) }
            Button(onClick = {
                sample = (sample + 1) % samples.size
                text = samples[sample]
            }) { Text(stringResource(R.string.debug_voice_next)) }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.debug_voice_text)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.debug_voice_volume, (volume * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { VoiceVolume.set(context, volume) },
        )
        val shown = scales ?: persona.voiceScales ?: DEFAULT_SCALES
        SCALE_NAMES.forEachIndexed { i, name ->
            Text("$name ${"%.2f".format(shown[i])}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = shown[i],
                onValueChange = { value -> scales = shown.copyOf().also { it[i] = (value * 20).roundToInt() / 20f } },
                valueRange = SCALE_RANGES[i],
            )
        }
        if (scales != null) {
            Button(onClick = { scales = null }) { Text(stringResource(R.string.debug_voice_scales_reset)) }
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    SectionHeader(stringResource(R.string.debug_probe))
    Button(
        onClick = { context.startActivity(Intent(context, ProbeActivity::class.java)) },
        modifier = Modifier.padding(horizontal = Spacing.base),
    ) { Text(stringResource(R.string.debug_probe_open)) }
    var likeness by remember { mutableStateOf(LooseMatch.likeness(context)) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base)) {
        Text(
            stringResource(R.string.debug_probe_likeness, (likeness * 100).roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = likeness,
            onValueChange = { likeness = (it * 20).roundToInt() / 20f },
            onValueChangeFinished = { LooseMatch.setLikeness(context, likeness) },
            valueRange = 0.5f..1f,
        )
        Text(
            stringResource(R.string.debug_probe_likeness_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader(stringResource(R.string.debug_vocabulary))
    VocabularyLookup()

    SectionHeader(stringResource(R.string.debug_gate))
    GateNumberField()
}

/** Phase F's gate (R1): `adb logcat -s DarekVocabulary`. */
@Composable
private fun VocabularyLookup() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var words by rememberSaveable { mutableStateOf("jaka pogoda darek pogodzisko") }
    var result by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        OutlinedTextField(
            value = words,
            onValueChange = { words = it },
            label = { Text(stringResource(R.string.debug_vocabulary_words)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = {
            scope.launch {
                result =
                    runCatching { withContext(Dispatchers.IO) { DebugVocabulary.lookUp(context, words) } }
                        .getOrElse { "failed: $it" }
                        .also { Log.i(DebugVocabulary.TAG, it) }
            }
        }) { Text(stringResource(R.string.debug_vocabulary_check)) }
        if (result.isNotEmpty()) {
            Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private object DebugVocabulary {
    const val TAG = "DarekVocabulary"
    private var model: Model? = null

    @Synchronized
    fun lookUp(
        context: Context,
        words: String,
    ): String {
        val loaded = model ?: Model(RecogniserModel.unpack(context.applicationContext).path).also { model = it }
        return tokensOf(words.lowercase()).joinToString(" ") {
            "$it=${if (Vocabulary.knows(loaded, it)) "known" else "unknown"}"
        }
    }
}

@Composable
private fun GateNumberField() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var saved by remember { mutableStateOf(GateNumber.get(context).orEmpty()) }
    var number by rememberSaveable { mutableStateOf(saved) }
    var canCall by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val askToCall =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canCall = it }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.base).padding(bottom = Spacing.base),
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        Button(onClick = {
            GateNumber.set(context, number)
            saved = number.trim()
            keyboard?.hide()
            if (saved.isNotEmpty() && !canCall) askToCall.launch(Manifest.permission.CALL_PHONE)
        }) { Text(stringResource(R.string.debug_gate_save)) }
        Text(
            stringResource(
                when {
                    number.trim() != saved -> R.string.debug_gate_unsaved
                    saved.isEmpty() -> R.string.debug_gate_off
                    canCall -> R.string.debug_gate_on
                    else -> R.string.debug_gate_no_permission
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text(stringResource(R.string.debug_gate_number)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Piper's inference settings in the order its `scales` input takes them; null means the voice's own.
private val SCALE_NAMES = listOf("noise_scale", "length_scale", "noise_w")
private val SCALE_RANGES = listOf(0f..1f, 0.6f..1.6f, 0f..1f)

// Both voices' configs carry these; a Persona's own `voiceScales` take their place.
private val DEFAULT_SCALES = floatArrayOf(0.667f, 1f, 0.8f)

private val samples =
    listOf(
        "Co tam, mordo?",
        "Otwieram bramę.",
        "Jest 21:30, a o 7:05 zadzwoni budzik.",
        "Na dworze jest -5°C, a w nocy będzie -13,2°C.",
        "Jutro +2°, w południe 0°C, wieczorem -0,5°C.",
        "Dziś 22°C, wczoraj 1°, w lutym -25°C, a woda ma 21,5 °C.",
        "Chrząszcz brzmi w trzcinie, a wąskie mosty drżą.",
    )

private object DebugVoice {
    private const val TAG = "DarekVoice"
    private val lock = Mutex()
    private var loaded: Pair<Persona, Voice>? = null

    suspend fun say(
        context: Context,
        text: String,
        volume: Float,
        scales: FloatArray?,
    ): String =
        lock.withLock {
            val persona =
                (context.applicationContext as MainApplication)
                    .container.preferences.persona
                    .first()
            val (voice, loadMs) = withContext(Dispatchers.Default) { voiceFor(context, persona) }
            val started = SystemClock.elapsedRealtime()
            val used = scales ?: voice.defaultScales
            val samples = withContext(Dispatchers.Default) { voice.synthesize(text, used) }
            val synthMs = SystemClock.elapsedRealtime() - started
            val end = ReplyPlayer.play(samples, volume)
            val audioMs = samples.size * 1000L / Voice.SAMPLE_RATE
            "persona=$persona scales=${used.joinToString(
                "/",
            )} load=${loadMs}ms synth=${synthMs}ms audio=${audioMs}ms end=$end"
                .also { Log.i(TAG, "$it text=\"$text\"") }
        }

    private fun voiceFor(
        context: Context,
        persona: Persona,
    ): Pair<Voice, Long> {
        loaded?.takeIf { it.first == persona }?.let { return it.second to 0L }
        loaded?.second?.close()
        val started = SystemClock.elapsedRealtime()
        val voice = Voice.load(context, persona.voice, persona.voiceScales)
        loaded = persona to voice
        return voice to SystemClock.elapsedRealtime() - started
    }
}
