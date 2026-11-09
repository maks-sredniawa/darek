package app.darek.listen

import app.darek.listen.CancelWindow.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class CancelWindowTest {
    private val finishedAt = 10_000L
    private val window = CancelWindow()

    private fun finished() = window.replyFinished(finishedAt)

    private fun after(ms: Long) = finishedAt + ms

    @Test
    fun anuluj_at_1_9_s_cancels() {
        finished()
        assertEquals(Decision.CANCELLED, window.heard(listOf("anuluj"), after(1_900)))
    }

    @Test
    fun stop_cancels_with_no_name_and_among_other_words() {
        finished()
        assertEquals(Decision.CANCELLED, window.heard(listOf("marek", "stop"), after(500)))
    }

    @Test
    fun anuluj_at_2_1_s_is_too_late_and_the_dial_stands() {
        finished()
        assertEquals(Decision.DIAL, window.heard(listOf("anuluj"), after(2_100)))
    }

    @Test
    fun other_words_in_the_window_change_nothing() {
        finished()
        assertEquals(Decision.WAIT, window.heard(listOf("darek", "otwórz"), after(1_000)))
    }

    @Test
    fun the_window_runs_its_two_seconds_and_then_dials() {
        finished()
        assertEquals(Decision.WAIT, window.tick(after(1_900)))
        assertEquals(Decision.DIAL, window.tick(after(2_100)))
    }

    @Test
    fun no_dial_before_the_reply_is_finished_however_much_time_passes() {
        assertEquals(Decision.WAIT, window.tick(1_000_000))
        assertEquals(Decision.WAIT, window.heard(listOf("darek"), 2_000_000))
    }

    @Test
    fun anuluj_said_over_the_reply_is_not_heard() {
        assertEquals(Decision.WAIT, window.heard(listOf("anuluj"), finishedAt - 100))
        finished()
        assertEquals(Decision.DIAL, window.tick(after(2_100)))
    }

    @Test
    fun the_window_counts_from_the_end_of_the_reply_not_from_its_start() {
        window.tick(1_000)
        finished()
        assertEquals(Decision.WAIT, window.tick(after(1_000)))
    }

    @Test
    fun listening_stopping_before_the_reply_is_finished_means_no_dial_whatever_follows() {
        assertEquals(Decision.STOPPED, window.listeningStopped())
        assertEquals(Decision.STOPPED, window.replyFinished(finishedAt))
        assertEquals(Decision.STOPPED, window.tick(after(60_000)))
    }

    @Test
    fun listening_stopping_in_the_window_means_no_dial_whatever_follows() {
        finished()
        window.tick(after(1_000))
        assertEquals(Decision.STOPPED, window.listeningStopped())
        assertEquals(Decision.STOPPED, window.tick(after(60_000)))
        assertEquals(Decision.STOPPED, window.heard(listOf("darek"), after(60_000)))
    }

    @Test
    fun a_cancel_stands_whatever_follows() {
        finished()
        window.heard(listOf("stop"), after(1_000))
        assertEquals(Decision.CANCELLED, window.tick(after(60_000)))
        assertEquals(Decision.CANCELLED, window.listeningStopped())
    }

    @Test
    fun a_decided_dial_is_not_undone_by_a_late_cancel_word() {
        finished()
        window.tick(after(2_100))
        assertEquals(Decision.DIAL, window.heard(listOf("anuluj"), after(2_200)))
    }
}
