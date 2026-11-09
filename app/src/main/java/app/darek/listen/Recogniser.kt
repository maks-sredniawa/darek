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
    ) : Heard

    data class Final(
        override val tokens: List<String>,
    ) : Heard
}

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
                check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord did not initialise" }
                val rate = SAMPLE_RATE.toFloat()
                (grammar?.let { Recognizer(model, rate, it) } ?: Recognizer(model, rate)).use { recognizer ->
                    record.startRecording()
                    onRecording()
                    val buffer = ShortArray(CHUNK)
                    var lastPartial = emptyList<String>()
                    var holding = false

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
                        check(read >= 0) { "AudioRecord.read returned $read" }
                        if (recognizer.acceptWaveForm(buffer, read)) {
                            lastPartial = emptyList()
                            hold(false)
                            emit(Heard.Final(field(recognizer.result, "text")))
                        } else {
                            val partial = field(recognizer.partialResult, "partial")
                            if (partial != lastPartial) {
                                lastPartial = partial
                                hold(holdAfter != null && partial.lastOrNull() == holdAfter)
                                emit(Heard.Partial(partial))
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
    }
}
