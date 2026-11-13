package app.darek.fuchy

import app.darek.listen.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** phase-F.md, *Tests*: fails towards a fucha stealing another's utterances. */
class ClashTest {
    private val gate = ownCommand("gate")
    private val saySomething = ownCommand("say-something")
    private val enabled = ownFucha.commands

    private fun candidate(
        phrase: String,
        decoys: List<String> = emptyList(),
    ): Fucha =
        accepted(
            weather.withCommand(
                CommandSource(id = "nowa", phrase = phrase, action = "speak", reply = "Okej.", decoys = decoys),
            ),
        )

    private fun clashesOf(
        phrase: String,
        decoys: List<String> = emptyList(),
    ) = clashes(candidate(phrase, decoys), enabled)

    @Test
    fun a_phrase_far_from_everything_clashes_with_nothing() {
        assertEquals(emptyList<Clash>(), clashesOf("jaka pogoda", listOf("jaka przygoda")))
    }

    @Test
    fun identical_phrases_are_never_both_on_after_normalising() {
        listOf("otwórz bramę", "Otwórz, bramę!", "  OTWÓRZ   bramę ").forEach { phrase ->
            val found = clashesOf(phrase)
            assertEquals(phrase, listOf(Clash.Holds(candidate(phrase).commands.single(), gate)), found)
            assertTrue(found.single().neverBothOn)
        }
    }

    @Test
    fun one_phrase_holding_the_other_s_words_adjacent_and_in_order_is_never_both_on() {
        listOf("otwórz bramę dwa", "proszę otwórz bramę", "hej powiedz coś teraz").forEach { phrase ->
            val found = clashesOf(phrase)
            assertTrue(phrase, found.any { it is Clash.Holds && it.neverBothOn })
        }
        assertTrue(clashesOf("powiedz").any { it is Clash.Holds && it.enabled == saySomething })
        assertFalse(clashesOf("otwórz szybko bramę").any { it is Clash.Holds })
        assertFalse(clashesOf("bramę otwórz").any { it is Clash.Holds })
    }

    @Test
    fun a_phrase_within_two_letters_installs_off_with_a_warning() {
        listOf("otwórz bramy", "otwórz bram", "otworz brame", "powiedz coś!").forEach { phrase ->
            val found = clashesOf(phrase).filterNot { it is Clash.NearWord }
            val expected = if (phrase == "powiedz coś!") Clash.Holds::class else Clash.SoundsLike::class
            assertEquals(phrase, expected, found.single()::class)
        }
        val near = clashesOf("otwórz bramy").filterIsInstance<Clash.SoundsLike>().single()
        assertEquals(gate, near.enabled)
        assertFalse(near.neverBothOn)
        assertTrue(clashesOf("otwórz drzwi").none { it is Clash.SoundsLike })
    }

    @Test
    fun a_word_within_two_letters_of_either_persona_s_name_installs_off() {
        val found = clashesOf("jaka pogoda", listOf("darik", "dorota"))
        assertEquals(
            setOf(Clash.NearName("darik", Persona.DAREK), Clash.NearName("darik", Persona.DARIA)),
            found.toSet(),
        )
        assertTrue(
            clashesOf("daria pogoda").none {
                it is Clash.NearName &&
                    it.word == "daria" &&
                    it.persona == Persona.DARIA
            },
        )
        assertEquals(listOf(Clash.NearName("dariat", Persona.DARIA)), clashesOf("dariat pogoda"))
        assertTrue(found.none { it.neverBothOn })
    }

    @Test
    fun a_word_within_two_letters_of_another_enabled_command_s_word_installs_off() {
        val found = clashesOf("jaka pogoda", listOf("brama"))
        assertEquals(listOf(Clash.NearWord("brama", gate, "bramę")), found)
        assertEquals(listOf(Clash.NearWord("powiedzż", saySomething, "powiedz")), clashesOf("powiedzż pogoda"))
        assertEquals(emptyList<Clash>(), clashesOf("jaka pogoda", listOf("bramę", "otwórz")))
        assertTrue(found.none { it.neverBothOn })
    }

    @Test
    fun a_fucha_does_not_clash_with_its_own_commands_already_on() {
        val update = candidate("otwórz bramę dwa", listOf("brama")).copy(id = "darek")
        assertEquals(emptyList<Clash>(), clashes(update, enabled))
        assertEquals(emptyList<Clash>(), clashes(ownFucha, enabled))
    }

    @Test
    fun the_distance_counts_insertions_deletions_and_substitutions() {
        assertEquals(0, editDistance("bramę", "bramę"))
        assertEquals(1, editDistance("bramę", "brama"))
        assertEquals(1, editDistance("darek", "darekk"))
        assertEquals(1, editDistance("darek", "dare"))
        assertEquals(2, editDistance("darek", "daria"))
        assertEquals(5, editDistance("", "darek"))
    }

    @Test
    fun normalising_lower_cases_drops_punctuation_and_collapses_whitespace() {
        assertEquals("otwórz bramę", normalised("  Otwórz,\tBRAMĘ!! "))
        assertEquals("zażółć gęślą jaźń", normalised("Zażółć gęślą jaźń."))
        assertEquals("", normalised("?!"))
    }
}
