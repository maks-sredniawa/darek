package app.darek.listen

import org.junit.Assert.assertEquals
import org.junit.Test

/** Written by hand from ADR-0009's rule and phase 1's script. A wrong row here is a false fire. */
class CommandMatchTest {
    private val all = Command.entries

    private fun heard(
        text: String,
        persona: Persona = Persona.DAREK,
    ) = commandIn(tokensOf(text), all, persona)

    @Test
    fun the_commands_fire() {
        assertEquals(Command.SAY_SOMETHING, heard("darek powiedz coś"))
        assertEquals(Command.GATE, heard("darek otwórz bramę"))
    }

    @Test
    fun words_before_and_after_are_ignored() {
        assertEquals(Command.SAY_SOMETHING, heard("[unk] darek powiedz coś"))
        assertEquals(Command.SAY_SOMETHING, heard("darek powiedz coś [unk]"))
        assertEquals(Command.SAY_SOMETHING, heard("[unk] [unk] darek powiedz coś marek"))
        assertEquals(Command.GATE, heard("darek otwórz bramę [unk]"))
        assertEquals(Command.GATE, heard("marek darek otwórz bramę"))
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
        assertEquals(null, commandIn(tokensOf("darek otwórz bramę"), listOf(Command.SAY_SOMETHING), Persona.DAREK))
        assertEquals(
            Command.SAY_SOMETHING,
            commandIn(tokensOf("darek powiedz coś"), listOf(Command.SAY_SOMETHING), Persona.DAREK),
        )
    }

    @Test
    fun the_first_command_heard_wins() {
        assertEquals(Command.GATE, heard("darek otwórz bramę darek powiedz coś"))
    }

    @Test
    fun tokens_split_on_any_whitespace() {
        assertEquals(listOf("darek", "powiedz", "coś"), tokensOf(" darek  powiedz\tcoś\n"))
    }

    @Test
    fun hailed_without_a_command_he_asks_to_be_set_up_until_a_command_that_calls_is_on() {
        val sayOnly = listOf(Command.SAY_SOMETHING)
        val setUp = Command.entries
        assertEquals(
            Reaction.Fires(Command.SAY_SOMETHING),
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
            listOf(listOf(Command.SAY_SOMETHING), Command.entries).forEach { enabled ->
                assertEquals(text, null, reactionTo(tokensOf(text), enabled, Persona.DAREK))
            }
        }
    }

    @Test
    fun a_command_fires_with_or_without_hej() {
        assertEquals(Reaction.Fires(Command.GATE), reactionTo(tokensOf("darek otwórz bramę"), all, Persona.DAREK))
        assertEquals(Reaction.Fires(Command.GATE), reactionTo(tokensOf("hej darek otwórz bramę"), all, Persona.DAREK))
    }

    @Test
    fun under_daria_her_name_starts_the_commands_and_his_does_not() {
        assertEquals(Command.SAY_SOMETHING, heard("daria powiedz coś", Persona.DARIA))
        assertEquals(Command.GATE, heard("hej daria otwórz bramę", Persona.DARIA))
        listOf("darek powiedz coś", "dario powiedz coś", "darii otwórz bramę", "maria powiedz coś")
            .forEach { assertEquals(it, null, heard(it, Persona.DARIA)) }
        assertEquals(null, heard("daria powiedz coś"))
    }

    @Test
    fun the_other_persona_hailed_gets_the_correction_after_the_own_name_s_answers() {
        val sayOnly = listOf(Command.SAY_SOMETHING)
        val setUp = Command.entries
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
        assertEquals(Command.SAY_SOMETHING.reply.by(Persona.DAREK), Command.SAY_SOMETHING.reply.by(Persona.DARIA))
    }
}
