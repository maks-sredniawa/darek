package app.darek.listen

import android.annotation.SuppressLint
import android.media.AudioManager

/** What the microphone gives Listening right now (phase-2.md, section 3). */
enum class Hearing { HEARS, PAUSED_FOR_CALL, CANNOT_HEAR }

/**
 * A run of exact zeros longer than [boundSamples] is a microphone that hears nothing: the platform
 * silencing an app's capture, or another app holding the microphone. Real silence is never exact zero.
 */
class ZeroRun(
    private val boundSamples: Int,
) {
    private var run = 0

    /** Feeds the next [count] samples; true while the current run of zeros exceeds the bound. */
    fun feed(
        samples: ShortArray,
        count: Int,
    ): Boolean {
        var lastSound = -1
        for (i in count - 1 downTo 0) {
            if (samples[i] != 0.toShort()) {
                lastSound = i
                break
            }
        }
        run = if (lastSound < 0) run + count else count - 1 - lastSound
        return run > boundSamples
    }
}

// Constants are inlined at compile time, so the newer modes are plain ints on older APIs.
@SuppressLint("InlinedApi")
private val CALL_MODES =
    setOf(
        AudioManager.MODE_IN_CALL,
        AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_RINGTONE,
        AudioManager.MODE_CALL_SCREENING,
        AudioManager.MODE_CALL_REDIRECT,
        AudioManager.MODE_COMMUNICATION_REDIRECT,
    )

/** Whether the phone's audio mode says a call is ringing or under way, on every API level. */
fun inCall(mode: Int): Boolean = mode in CALL_MODES
