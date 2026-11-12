package app.darek.listen

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.vosk.Model
import org.vosk.Recognizer

sealed interface Heard {
    val tokens: List<String>

    data class Partial(
        override val tokens: List<String>,
        val steadyMs: Long = 0,
    ) : Heard

    data class Final(
        override val tokens: List<String>,
    ) : Heard
}

/** The recording could not start, or stopped giving audio: Listening shows *cannot hear* and tries again. */
class MicrophoneUnavailable(
    message: String,
) : IllegalStateException(message)

/** The microphone into Vosk, as a cold [Flow]: collecting it records, cancelling it stops. Writes no audio. */
class Recogniser(
    private val model: Model,
) {
    /**
     * A `null` grammar is free decoding, phase 1's verdict 4 only. While a partial ends on [holdAfter],
     * the utterance waits longer before it ends, so a user can pause after the name and still say one
     * Command in one final result (ADR-0009).
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun heard(
        grammar: String?,
        holdAfter: String? = null,
        onRecording: () -> Unit = {},
        onHears: (Boolean) -> Unit = {},
    ): Flow<Heard> =
        flow {
            val record =
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(
                        AudioRecord.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_IN_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ),
                        CHUNK * 4,
                    ),
                )
            try {
                if (record.state !=
                    AudioRecord.STATE_INITIALIZED
                ) {
                    throw MicrophoneUnavailable("AudioRecord did not initialise")
                }
                val rate = SAMPLE_RATE.toFloat()
                (grammar?.let { Recognizer(model, rate, it) } ?: Recognizer(model, rate)).use { recognizer ->
                    record.startRecording()
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        throw MicrophoneUnavailable("AudioRecord did not start")
                    }
                    onRecording()
                    val buffer = ShortArray(CHUNK)
                    var lastPartial = emptyList<String>()
                    var steadyMs = 0L
                    var holding = false
                    val zeros = ZeroRun(SAMPLE_RATE * SILENCED_S)
                    var hears = true

                    fun hold(wanted: Boolean) {
                        if (wanted == holding) return
                        holding = wanted
                        if (wanted) {
                            recognizer.setEndpointerDelays(START_SILENCE_S, HOLD_SILENCE_S, UTTERANCE_MAX_S)
                        } else {
                            recognizer.setEndpointerMode(MODEL_ENDPOINTER_MODE)
                        }
                    }
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = record.read(buffer, 0, buffer.size)
                        if (read < 0) throw MicrophoneUnavailable("AudioRecord.read returned $read")
                        val silenced = zeros.feed(buffer, read)
                        if (silenced == hears) {
                            hears = !silenced
                            onHears(hears)
                        }
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            lastPartial = emptyList()
                            steadyMs = 0
                            hold(false)
                            emit(Heard.Final(field(recognizer.result, "text")))
                        } else {
                            val partial = field(recognizer.partialResult, "partial")
                            if (partial != lastPartial) {
                                lastPartial = partial
                                steadyMs = 0
                                hold(holdAfter != null && partial.lastOrNull() == holdAfter)
                                emit(Heard.Partial(partial))
                            } else if (partial.isNotEmpty()) {
                                steadyMs += read * 1000L / SAMPLE_RATE
                                emit(Heard.Partial(partial, steadyMs))
                            }
                        }
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }.flowOn(Dispatchers.IO)

    private fun field(
        json: String,
        key: String,
    ): List<String> =
        tokensOf(
            Json
                .parseToJsonElement(json)
                .jsonObject[key]
                ?.jsonPrimitive
                ?.content
                .orEmpty(),
        )

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHUNK = SAMPLE_RATE / 10

        // setEndpointerDelays takes rules 1 and 5 too: these are Kaldi's, which model.conf leaves alone.
        private const val START_SILENCE_S = 5f
        private const val UTTERANCE_MAX_S = 20f
        private const val HOLD_SILENCE_S = 3.5f
        private const val MODEL_ENDPOINTER_MODE = 0
        private const val SILENCED_S = 1
    }
}
