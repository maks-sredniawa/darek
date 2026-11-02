package app.darek.speak

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** A rendered line kept loaded on a track, because building one when it is needed adds to the delay. */
class LoadedLine(
    samples: ShortArray,
) : AutoCloseable {
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
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also {
                val written = it.write(samples, 0, samples.size)
                if (written != samples.size) {
                    it.release()
                    error("wrote $written of ${samples.size} samples")
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
