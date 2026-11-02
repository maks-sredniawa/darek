package app.darek.listen

import org.junit.Assert.assertEquals
import org.junit.Test

class NameHeardTest {
    private fun partial(vararg tokens: String) = Heard.Partial(tokens.toList())

    private fun firsts(vararg results: Heard): List<Boolean> {
        val nameHeard = NameHeard("daria")
        return results.map(nameHeard::isFirst)
    }

    @Test
    fun repeated_partials_chime_once() {
        assertEquals(
            listOf(false, true, false),
            firsts(partial("hej"), partial("hej", "daria"), partial("hej", "daria", "powiedz")),
        )
    }

    @Test
    fun a_partial_that_loses_the_name_and_regains_it_does_not_chime_again() {
        assertEquals(
            listOf(true, false, false),
            firsts(partial("daria"), partial("dario"), partial("daria")),
        )
    }

    @Test
    fun the_next_utterance_chimes_again() {
        assertEquals(
            listOf(true, false, true),
            firsts(partial("daria"), Heard.Final(listOf("daria")), partial("daria")),
        )
    }

    @Test
    fun the_other_name_does_not_chime() {
        assertEquals(listOf(false), firsts(partial("hej", "darek")))
    }
}
