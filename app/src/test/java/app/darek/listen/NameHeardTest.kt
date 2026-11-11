package app.darek.listen

import org.junit.Assert.assertEquals
import org.junit.Test

class NameHeardTest {
    private val still = NameHeard.STEADY_MS

    private fun partial(
        vararg tokens: String,
        steadyMs: Long = still,
    ) = Heard.Partial(tokens.toList(), steadyMs)

    private fun firsts(vararg results: Heard): List<Boolean> {
        val nameHeard = NameHeard("daria")
        return results.map(nameHeard::isFirst)
    }

    @Test
    fun a_hail_answers_only_once_it_has_held_still_long_enough() {
        assertEquals(
            listOf(false, false, true),
            firsts(
                partial("hej", "daria", steadyMs = 0),
                partial("hej", "daria", steadyMs = still - 100),
                partial("hej", "daria", steadyMs = still),
            ),
        )
    }

    @Test
    fun a_hail_that_flickers_past_on_the_way_to_another_name_does_not_answer() {
        assertEquals(
            listOf(false, false, false, false),
            firsts(
                partial("hej", "daria", steadyMs = 0),
                partial("hej", steadyMs = 0),
                partial("hej", "maria", steadyMs = 0),
                partial("hej", "maria", steadyMs = still + 500),
            ),
        )
    }

    @Test
    fun repeated_partials_answer_once() {
        assertEquals(
            listOf(false, true, false, false),
            firsts(
                partial("hej"),
                partial("hej", "daria"),
                partial("hej", "daria", steadyMs = still + 100),
                partial("hej", "daria", "powiedz"),
            ),
        )
    }

    @Test
    fun a_partial_that_loses_the_name_and_regains_it_does_not_answer_again() {
        assertEquals(
            listOf(true, false, false),
            firsts(partial("hej", "daria"), partial("hej", "dario"), partial("hej", "daria")),
        )
    }

    @Test
    fun the_next_utterance_answers_again() {
        assertEquals(
            listOf(true, false, true),
            firsts(partial("hej", "daria"), Heard.Final(listOf("hej", "daria")), partial("hej", "daria")),
        )
    }

    @Test
    fun the_name_without_hej_right_before_it_does_not_answer() {
        assertEquals(
            listOf(false, false, false, false),
            firsts(
                partial("daria"),
                partial("daria", "idziesz"),
                partial("hej", "[unk]", "daria"),
                partial("daria", "hej"),
            ),
        )
    }

    @Test
    fun the_other_name_does_not_answer() {
        assertEquals(listOf(false), firsts(partial("hej", "darek")))
    }
}
