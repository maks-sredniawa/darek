package app.darek.listen

import app.darek.listen.CancelWindow.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class CancelWindowTest {
    private val startedAt = 10_000L
    private val window = CancelWindow()

    private fun started() = window.recordingStarted(startedAt)

    private fun after(ms: Long) = startedAt + ms

    @Test
    fun anuluj_at_1_9_s_cancels() {
        started()
        assertEquals(Decision.CANCELLED, window.heard(listOf("anuluj"), after(1_900)))
    }

    @Test
    fun stop_cancels_with_no_name_and_among_other_words() {
        started()
        assertEquals(Decision.CANCELLED, window.heard(listOf("marek", "stop"), after(500)))
    }

    @Test
    fun anuluj_at_2_1_s_is_too_late_and_the_dial_stands() {
        started()
        assertEquals(Decision.DIAL, window.heard(listOf("anuluj"), after(2_100)))
    }

    @Test
    fun other_words_in_the_window_change_nothing() {
        started()
        assertEquals(Decision.WAIT, window.heard(listOf("darek", "otwórz"), after(1_000)))
    }

    @Test
    fun the_window_runs_its_two_seconds_and_then_dials() {
        started()
        assertEquals(Decision.WAIT, window.tick(after(1_900)))
        assertEquals(Decision.DIAL, window.tick(after(2_100)))
    }

    @Test
    fun no_dial_before_recording_starts_however_much_time_passes() {
        assertEquals(Decision.WAIT, window.tick(1_000_000))
        assertEquals(Decision.WAIT, window.heard(listOf("darek"), 2_000_000))
    }

    @Test
    fun anuluj_said_before_recording_starts_is_not_heard() {
        assertEquals(Decision.WAIT, window.heard(listOf("anuluj"), startedAt - 100))
        started()
        assertEquals(Decision.DIAL, window.tick(after(2_100)))
    }

    @Test
    fun the_window_counts_from_when_recording_starts_not_from_earlier() {
        window.tick(1_000)
        started()
        assertEquals(Decision.WAIT, window.tick(after(1_000)))
    }

    @Test
    fun listening_stopping_before_recording_starts_means_no_dial_whatever_follows() {
        assertEquals(Decision.STOPPED, window.listeningStopped())
        assertEquals(Decision.STOPPED, window.recordingStarted(startedAt))
        assertEquals(Decision.STOPPED, window.tick(after(60_000)))
    }

    @Test
    fun listening_stopping_in_the_window_means_no_dial_whatever_follows() {
        started()
        window.tick(after(1_000))
        assertEquals(Decision.STOPPED, window.listeningStopped())
        assertEquals(Decision.STOPPED, window.tick(after(60_000)))
        assertEquals(Decision.STOPPED, window.heard(listOf("darek"), after(60_000)))
    }

    @Test
    fun a_cancel_stands_whatever_follows() {
        started()
        window.heard(listOf("stop"), after(1_000))
        assertEquals(Decision.CANCELLED, window.tick(after(60_000)))
        assertEquals(Decision.CANCELLED, window.listeningStopped())
    }

    @Test
    fun a_decided_dial_is_not_undone_by_a_late_cancel_word() {
        started()
        window.tick(after(2_100))
        assertEquals(Decision.DIAL, window.heard(listOf("anuluj"), after(2_200)))
    }

    @Test
    fun a_microphone_that_cannot_hear_refuses_the_dial_for_good() {
        started()
        assertEquals(Decision.CANNOT_HEAR, window.cannotHear())
        assertEquals(Decision.CANNOT_HEAR, window.tick(after(2_100)))
        assertEquals(Decision.CANNOT_HEAR, window.heard(listOf("anuluj"), after(2_200)))
    }

    @Test
    fun cannot_hear_after_the_dial_is_decided_changes_nothing() {
        started()
        window.tick(after(2_100))
        assertEquals(Decision.DIAL, window.cannotHear())
    }
}
