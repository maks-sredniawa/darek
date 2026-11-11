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
    fun repeated_partials_answer_once() {
        assertEquals(
            listOf(false, true, false),
            firsts(partial("hej"), partial("hej", "daria"), partial("hej", "daria", "powiedz")),
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
