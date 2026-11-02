package app.darek.speak

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** A short low hum, like a person's "mm", that says the name was heard. Rendered here, so it adds no asset and no licence. */
object Chime {
    private const val SECONDS = 0.150
    private const val ATTACK = 0.030
    private const val DECAY = 0.060
    private const val START_HZ = 659.0
    private const val END_HZ = 698.0
    private const val VIBRATO_HZ = 5.5
    private const val VIBRATO_DEPTH = 0.008
    private const val PEAK = 0.25

    val samples: ShortArray by lazy {
        var phase = 0.0
        val wave =
            DoubleArray((Voice.SAMPLE_RATE * SECONDS).toInt()) { i ->
                val t = i.toDouble() / Voice.SAMPLE_RATE
                val hz =
                    (START_HZ + (END_HZ - START_HZ) * t / SECONDS) * (1 + VIBRATO_DEPTH * sin(2 * PI * VIBRATO_HZ * t))
                phase += 2 * PI * hz / Voice.SAMPLE_RATE
                rise(t / ATTACK) * rise((SECONDS - t) / DECAY) * sin(phase)
            }
        val scale = PEAK * Short.MAX_VALUE / wave.maxOf { abs(it) }
        ShortArray(wave.size) { (wave[it] * scale).toInt().toShort() }
    }

    private fun rise(x: Double) = if (x >= 1) 1.0 else (1 - cos(PI * x)) / 2
}

/** The [Chime] kept loaded on a track, because building one when it is needed adds to the delay. */
class ChimePlayer : AutoCloseable {
    private val track =
        AudioTrack
            .Builder()
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    // With any other content type this usage leaves the assistant stream for media's, which can be muted.
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            ).setAudioFormat(
                AudioFormat
                    .Builder()
                    .setSampleRate(Voice.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            ).setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(Chime.samples.size * 2)
            .build()
            .also {
                val written = it.write(Chime.samples, 0, Chime.samples.size)
                if (written != Chime.samples.size) {
                    it.release()
                    error("wrote $written of ${Chime.samples.size} samples")
                }
            }

    fun play(volume: Float) {
        track.stop()
        check(track.reloadStaticData() == AudioTrack.SUCCESS) { "reloadStaticData failed" }
        track.setVolume(volume)
        track.play()
    }

    override fun close() = track.release()
}
