package app.darek.listen

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneTest {
    private val bound = 1_000

    private fun zeros(n: Int) = ShortArray(n)

    private fun sound(n: Int) = ShortArray(n) { 1 }

    @Test
    fun zeros_up_to_the_bound_still_hear_and_one_more_cannot() {
        val run = ZeroRun(bound)
        assertFalse(run.feed(zeros(600), 600))
        assertFalse(run.feed(zeros(400), 400))
        assertTrue(run.feed(zeros(1), 1))
    }

    @Test
    fun one_sample_of_sound_ends_the_run() {
        val run = ZeroRun(bound)
        run.feed(zeros(1_500), 1_500)
        val chunk = zeros(800).also { it[100] = 3 }
        assertFalse(run.feed(chunk, chunk.size))
        assertTrue(run.feed(zeros(400), 400))
    }

    @Test
    fun only_the_samples_read_count() {
        val chunk = sound(2_000).also { it.fill(0, 0, 500) }
        val run = ZeroRun(bound)
        assertFalse(run.feed(chunk, 500))
        assertFalse(run.feed(chunk, 500))
        assertTrue(run.feed(chunk, 1))
        assertFalse(ZeroRun(bound).feed(chunk, chunk.size))
    }

    @Test
    fun quiet_sound_is_not_silence() {
        val run = ZeroRun(bound)
        repeat(10) { assertFalse(run.feed(ShortArray(1_600) { if (it % 2 == 0) 1 else -1 }, 1_600)) }
    }

    @Test
    fun calls_ringing_and_screening_are_in_call_and_normal_is_not() {
        listOf(
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_CALL_SCREENING,
        ).forEach { assertTrue("mode $it", inCall(it)) }
        assertFalse(inCall(AudioManager.MODE_NORMAL))
        assertFalse(inCall(AudioManager.MODE_INVALID))
    }
}
