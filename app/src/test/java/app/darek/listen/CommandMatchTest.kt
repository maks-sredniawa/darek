package app.darek.listen

import app.darek.fuchy.Action
import app.darek.fuchy.ownCommand
import app.darek.fuchy.ownFucha
import org.junit.Assert.assertEquals
import org.junit.Test

/** Written by hand from ADR-0009's rule and phase 1's script. A wrong row here is a false fire. */
class CommandMatchTest {
    private val saySomething = ownCommand("say-something")
    private val gate = ownCommand("gate")
    private val sayOnly = listOf(saySomething)
    private val setUp = ownFucha.commands
    private val all = setUp
    private val installed = Command("jaka-pogoda", "pogoda", "jaka pogoda", Action.SPEAK, Line("Słonecznie."))

    private fun heard(
        text: String,
        persona: Persona = Persona.DAREK,
    ) = commandIn(tokensOf(text), all, persona)

    @Test
    fun the_commands_fire() {
        assertEquals(saySomething, heard("darek powiedz coś"))
        assertEquals(gate, heard("darek otwórz bramę"))
    }

    @Test
    fun words_before_and_after_are_ignored() {
        assertEquals(saySomething, heard("[unk] darek powiedz coś"))
        assertEquals(saySomething, heard("darek powiedz coś [unk]"))
        assertEquals(saySomething, heard("[unk] [unk] darek powiedz coś marek"))
        assertEquals(gate, heard("darek otwórz bramę [unk]"))
        assertEquals(gate, heard("marek darek otwórz bramę"))
    }

    @Test
    fun near_misses_do_not_fire() {
        listOf(
            "marek powiedz coś",
            "jarek otwórz bramę",
            "janek otwórz bramę",
            "darka otwórz bramę",
            "darku powiedz coś",
            "dawid powiedz coś",
            "otwórz bramę",
            "powiedz coś",
            "darek",
            "darek powiedz",
            "darek otwórz",
            "darek otwórz brama",
            "darek zamknij bramę",
            "darek powiedz [unk] coś",
            "darek powiedz mi coś",
            "powiedz coś darek",
            "otwórz bramę darek",
            "darek [unk] otwórz bramę",
            "darek [unk] brama [unk]",
            "darek bramę otwórz",
            "darek coś powiedz",
            "",
        ).forEach { assertEquals(it, null, heard(it)) }
    }

    @Test
    fun a_command_that_is_not_enabled_does_not_fire() {
        assertEquals(null, commandIn(tokensOf("darek otwórz bramę"), sayOnly, Persona.DAREK))
        assertEquals(
            saySomething,
            commandIn(tokensOf("darek powiedz coś"), sayOnly, Persona.DAREK),
        )
    }

    @Test
    fun the_first_command_heard_wins() {
        assertEquals(gate, heard("darek otwórz bramę darek powiedz coś"))
    }

    @Test
    fun tokens_split_on_any_whitespace() {
        assertEquals(listOf("darek", "powiedz", "coś"), tokensOf(" darek  powiedz\tcoś\n"))
    }

    @Test
    fun hailed_without_a_command_he_asks_to_be_set_up_until_a_command_that_calls_is_on() {
        assertEquals(
            Reaction.Fires(saySomething),
            reactionTo(tokensOf("darek powiedz coś"), sayOnly, Persona.DAREK),
        )
        listOf(
            "hej darek",
            "hej darek [unk]",
            "[unk] hej darek",
            "hej darek powiedz",
            "hej darek powiedz [unk] coś",
            "hej darek otwórz bramę",
        ).forEach { assertEquals(it, Reaction.NotConfigured, reactionTo(tokensOf(it), sayOnly, Persona.DAREK)) }
        listOf("hej darek", "hej darek powiedz [unk] coś", "hej darek otwórz")
            .forEach { assertEquals(it, Reaction.NotUnderstood, reactionTo(tokensOf(it), setUp, Persona.DAREK)) }
        listOf("marek powiedz coś", "darka", "darku powiedz coś", "powiedz coś", "[unk]", "", "hej marek")
            .forEach {
                assertEquals(it, null, reactionTo(tokensOf(it), sayOnly, Persona.DAREK))
                assertEquals(it, null, reactionTo(tokensOf(it), setUp, Persona.DAREK))
            }
    }

    @Test
    fun a_command_from_an_installed_fucha_counts_as_set_up_even_when_it_only_replies() {
        listOf(sayOnly + installed, listOf(installed), listOf(gate), setUp).forEach { enabled ->
            assertEquals(
                enabled.toString(),
                Reaction.NotUnderstood,
                reactionTo(tokensOf("hej darek"), enabled, Persona.DAREK),
            )
        }
        assertEquals(Reaction.NotConfigured, reactionTo(tokensOf("hej darek"), sayOnly, Persona.DAREK))
        assertEquals(Reaction.NotConfigured, reactionTo(tokensOf("hej daria"), sayOnly, Persona.DARIA))
        assertEquals(Reaction.NotConfigured, reactionTo(tokensOf("hej darek"), emptyList(), Persona.DAREK))
        assertEquals(
            Reaction.Fires(installed),
            reactionTo(tokensOf("darek jaka pogoda"), sayOnly + installed, Persona.DAREK),
        )
    }

    @Test
    fun darek_s_own_commands_log_the_names_they_had_as_an_enum() {
        assertEquals("SAY_SOMETHING", saySomething.label)
        assertEquals("GATE", gate.label)
        assertEquals("pogoda/jaka-pogoda", installed.label)
    }

    @Test
    fun the_name_without_hej_right_before_it_gets_no_answer() {
        listOf(
            "darek",
            "darek [unk]",
            "[unk] darek",
            "darek idziesz",
            "darek powiedz",
            "darek powiedz [unk] coś",
            "darek otwórz",
            "hej [unk] darek",
            "darek hej",
            "hej",
        ).forEach { text ->
            listOf(sayOnly, setUp).forEach { enabled ->
                assertEquals(text, null, reactionTo(tokensOf(text), enabled, Persona.DAREK))
            }
        }
    }

    @Test
    fun a_command_fires_with_or_without_hej() {
        assertEquals(Reaction.Fires(gate), reactionTo(tokensOf("darek otwórz bramę"), all, Persona.DAREK))
        assertEquals(Reaction.Fires(gate), reactionTo(tokensOf("hej darek otwórz bramę"), all, Persona.DAREK))
    }

    @Test
    fun under_daria_her_name_starts_the_commands_and_his_does_not() {
        assertEquals(saySomething, heard("daria powiedz coś", Persona.DARIA))
        assertEquals(gate, heard("hej daria otwórz bramę", Persona.DARIA))
        listOf("darek powiedz coś", "dario powiedz coś", "darii otwórz bramę", "maria powiedz coś")
            .forEach { assertEquals(it, null, heard(it, Persona.DARIA)) }
        assertEquals(null, heard("daria powiedz coś"))
    }

    @Test
    fun the_other_persona_hailed_gets_the_correction_after_the_own_name_s_answers() {
        listOf("hej darek", "hej darek powiedz coś", "hej darek otwórz bramę").forEach {
            assertEquals(it, Reaction.Corrected, reactionTo(tokensOf(it), sayOnly, Persona.DARIA))
            assertEquals(it, Reaction.Corrected, reactionTo(tokensOf(it), setUp, Persona.DARIA))
        }
        assertEquals(Reaction.Corrected, reactionTo(tokensOf("hej daria powiedz coś"), sayOnly, Persona.DAREK))
        assertEquals(Reaction.NotConfigured, reactionTo(tokensOf("hej daria hej darek"), sayOnly, Persona.DARIA))
        assertEquals(Reaction.NotUnderstood, reactionTo(tokensOf("hej darek hej daria"), setUp, Persona.DAREK))
        listOf(
            "darek",
            "darek powiedz coś",
            "darek otwórz bramę",
            "dario otwórz bramę",
            "hej dario",
            "darku",
            "marek",
        ).forEach {
            assertEquals(it, null, reactionTo(tokensOf(it), setUp, Persona.DARIA))
        }
    }

    @Test
    fun every_line_has_a_form_for_each_persona() {
        assertEquals("Sorry, nie załapałam.", NOT_UNDERSTOOD[2].by(Persona.DARIA))
        assertEquals("Sorry, nie załapałem.", NOT_UNDERSTOOD[2].by(Persona.DAREK))
        assertEquals(saySomething.reply.by(Persona.DAREK), saySomething.reply.by(Persona.DARIA))
    }

    @Test
    fun a_command_is_spoken_with_the_persona_s_name_in_front_in_polish() {
        assertEquals("Darek, otwórz bramę", gate.spoken(Persona.DAREK))
        assertEquals("Daria, powiedz coś", saySomething.spoken(Persona.DARIA))
    }
}
