package app.darek.fuchy

import app.darek.listen.Grammar
import app.darek.listen.Line
import app.darek.listen.Persona
import app.darek.listen.commandDecoys
import app.darek.listen.enabledCommands
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Base64

/** Checkpoint B is a refactor: Darek's own Commands as a bundled fucha must leave the recogniser hearing exactly what it heard. */
class OwnFuchaTest {
    @Test
    fun darek_s_own_fucha_holds_today_s_two_commands_and_nothing_more() {
        val fucha = ownFucha
        assertEquals("darek", fucha.id)
        assertEquals(true, fucha.bundled)
        assertEquals(null, fucha.signature)
        assertEquals(listOf(Permission.CALL), fucha.permissions)
        assertEquals(emptyList<String>(), fucha.addresses)
        assertEquals(emptyList<SentValue>(), fucha.sends)
        assertEquals(false, fucha.profanity)
        assertEquals(listOf("say-something", "gate"), fucha.commands.map { it.id })
        val (say, gate) = fucha.commands
        assertEquals("powiedz coś", say.phrase)
        assertEquals(Action.SPEAK, say.action)
        assertEquals(Line("Co tam, mordo?"), say.reply)
        assertEquals(emptyList<String>(), say.decoys)
        assertEquals("otwórz bramę", gate.phrase)
        assertEquals(Action.CALL, gate.action)
        assertEquals(Line("Otwieram bramę."), gate.reply)
        assertEquals(listOf("otwórz", "zamknij", "brama"), gate.decoys)
        assertEquals(true, fucha.commands.all { it.bundled && it.fucha == "darek" })
    }

    @Test
    fun the_asset_is_packed_from_its_readable_source() {
        val packed = Json.parseToJsonElement(ownFuchaFile.decodeToString()) as JsonObject
        assertArrayEquals(
            "run python3 scripts/pack-fucha.py",
            File("src/main/fuchy/darek.json").readBytes(),
            Base64.getDecoder().decode(packed.getValue("fucha").jsonPrimitive.content),
        )
    }

    @Test
    fun the_grammar_is_byte_for_byte_what_the_enum_built() {
        val on = listOf(ownFucha)
        before.forEach { (persona, number, json) ->
            assertEquals(
                "$persona $number",
                json,
                Grammar.json(enabledCommands(on, number), commandDecoys(on), persona),
            )
        }
    }

    @Test
    fun a_command_that_calls_is_enabled_only_with_a_number() {
        assertEquals(listOf("say-something"), enabledCommands(listOf(ownFucha), null).map { it.id })
        assertEquals(listOf("say-something", "gate"), enabledCommands(listOf(ownFucha), "123").map { it.id })
    }

    /** `Grammar.json` for each Persona, without and with a Number, captured from the enum before it went. */
    private val before =
        listOf(
            Triple(
                Persona.DAREK,
                null,
                "[\"darek powiedz coś\",\"darek\",\"powiedz\",\"coś\",\"hej darek\",\"hej\",\"marek\",\"jarek\",\"janek\",\"dawid\",\"maria\",\"marii\",\"darka\",\"darku\",\"darii\",\"darię\",\"dario\",\"daria\",\"otwórz\",\"zamknij\",\"brama\",\"marek powiedz coś\",\"jarek powiedz coś\",\"janek powiedz coś\",\"dawid powiedz coś\",\"maria powiedz coś\",\"marii powiedz coś\",\"darka powiedz coś\",\"darku powiedz coś\",\"darii powiedz coś\",\"darię powiedz coś\",\"dario powiedz coś\",\"daria powiedz coś\",\"[unk]\"]",
            ),
            Triple(
                Persona.DAREK,
                "123",
                "[\"darek powiedz coś\",\"darek\",\"powiedz\",\"coś\",\"darek otwórz bramę\",\"otwórz\",\"bramę\",\"hej darek\",\"hej\",\"marek\",\"jarek\",\"janek\",\"dawid\",\"maria\",\"marii\",\"darka\",\"darku\",\"darii\",\"darię\",\"dario\",\"daria\",\"zamknij\",\"brama\",\"marek powiedz coś\",\"marek otwórz bramę\",\"jarek powiedz coś\",\"jarek otwórz bramę\",\"janek powiedz coś\",\"janek otwórz bramę\",\"dawid powiedz coś\",\"dawid otwórz bramę\",\"maria powiedz coś\",\"maria otwórz bramę\",\"marii powiedz coś\",\"marii otwórz bramę\",\"darka powiedz coś\",\"darka otwórz bramę\",\"darku powiedz coś\",\"darku otwórz bramę\",\"darii powiedz coś\",\"darii otwórz bramę\",\"darię powiedz coś\",\"darię otwórz bramę\",\"dario powiedz coś\",\"dario otwórz bramę\",\"daria powiedz coś\",\"daria otwórz bramę\",\"stop\",\"anuluj\",\"[unk]\"]",
            ),
            Triple(
                Persona.DARIA,
                null,
                "[\"daria powiedz coś\",\"daria\",\"powiedz\",\"coś\",\"hej daria\",\"hej\",\"marek\",\"jarek\",\"janek\",\"dawid\",\"maria\",\"marii\",\"darka\",\"darku\",\"darii\",\"darię\",\"dario\",\"darek\",\"otwórz\",\"zamknij\",\"brama\",\"marek powiedz coś\",\"jarek powiedz coś\",\"janek powiedz coś\",\"dawid powiedz coś\",\"maria powiedz coś\",\"marii powiedz coś\",\"darka powiedz coś\",\"darku powiedz coś\",\"darii powiedz coś\",\"darię powiedz coś\",\"dario powiedz coś\",\"darek powiedz coś\",\"[unk]\"]",
            ),
            Triple(
                Persona.DARIA,
                "123",
                "[\"daria powiedz coś\",\"daria\",\"powiedz\",\"coś\",\"daria otwórz bramę\",\"otwórz\",\"bramę\",\"hej daria\",\"hej\",\"marek\",\"jarek\",\"janek\",\"dawid\",\"maria\",\"marii\",\"darka\",\"darku\",\"darii\",\"darię\",\"dario\",\"darek\",\"zamknij\",\"brama\",\"marek powiedz coś\",\"marek otwórz bramę\",\"jarek powiedz coś\",\"jarek otwórz bramę\",\"janek powiedz coś\",\"janek otwórz bramę\",\"dawid powiedz coś\",\"dawid otwórz bramę\",\"maria powiedz coś\",\"maria otwórz bramę\",\"marii powiedz coś\",\"marii otwórz bramę\",\"darka powiedz coś\",\"darka otwórz bramę\",\"darku powiedz coś\",\"darku otwórz bramę\",\"darii powiedz coś\",\"darii otwórz bramę\",\"darię powiedz coś\",\"darię otwórz bramę\",\"dario powiedz coś\",\"dario otwórz bramę\",\"darek powiedz coś\",\"darek otwórz bramę\",\"stop\",\"anuluj\",\"[unk]\"]",
            ),
        )
}
