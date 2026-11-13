package app.darek.fuchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A file Darek cannot read is refused, never a crash: it arrives from anywhere. */
class FuchaFileTest {
    private fun refusedFor(file: ByteArray): List<Refusal> = (checked(file, knowsAll) as Checked.Refused).refusals

    @Test
    fun a_well_formed_file_reads_and_keeps_its_signature_unverified() {
        val read = parse(fileOf(weather, signature = "bm90IGEgc2lnbmF0dXJl")) as Parsed.Read
        assertEquals(weather, read.source)
        assertEquals("bm90IGEgc2lnbmF0dXJl", read.signature)
        assertNull((parse(fileOf(weather)) as Parsed.Read).signature)
        assertEquals(
            "bm90IGEgc2lnbmF0dXJl",
            (checked(fileOf(weather, "bm90IGEgc2lnbmF0dXJl"), knowsAll) as Checked.Accepted).fucha.signature,
        )
    }

    @Test
    fun what_cannot_be_read_is_refused_as_unreadable() {
        val inner = """{"format":1,"id":"x"}""".toByteArray()
        listOf(
            ByteArray(0),
            "not json".toByteArray(),
            "[]".toByteArray(),
            """{"signature":"abc"}""".toByteArray(),
            """{"fucha":"%%% not base64 %%%"}""".toByteArray(),
            envelope("not json".toByteArray()),
            envelope("[1]".toByteArray()),
            envelope("""{"id":"x"}""".toByteArray()),
            envelope("""{"format":"one"}""".toByteArray()),
            envelope("""{"format":0}""".toByteArray()),
            envelope(inner),
            envelope(byteArrayOf(0x7B, 0xC3.toByte(), 0x28, 0x7D)),
            envelope("""{"format":1,"profanity":"yes"}""".toByteArray()),
            fileOf(weather).copyOfRange(0, 20),
        ).forEach { assertEquals(String(it), listOf(Refusal.Unreadable), refusedFor(it)) }
    }

    @Test
    fun a_newer_format_needs_a_newer_darek_whatever_else_it_holds() {
        val refusals = refusedFor(envelope("""{"format":2,"anything":{"new":true}}""".toByteArray()))
        assertEquals(listOf(Refusal.NewerFormat(2)), refusals)
        assertTrue(refusals.single() is Refusal.NeedsNewerDarek)
    }

    @Test
    fun unknown_names_in_any_list_need_a_newer_darek_and_do_not_crash() {
        val source =
            weather.copy(
                permissions = listOf("internet", "location"),
                sends = listOf("chosen_city", "approximate_location"),
                commands =
                    weather.commands +
                        weather.commands[0].copy(id = "pogoda-jutro", phrase = "pogoda jutro", action = "weather"),
            )
        val refusals = refusedFor(fileOf(source))
        assertEquals(
            setOf(
                Refusal.UnknownName(NamedList.PERMISSION, "location"),
                Refusal.UnknownName(NamedList.SENT_VALUE, "approximate_location"),
                Refusal.UnknownName(NamedList.ACTION, "weather"),
            ),
            refusals.toSet(),
        )
        assertTrue(refusals.all { it is Refusal.NeedsNewerDarek })
    }

    @Test
    fun list_values_are_their_enum_names_in_lower_case_only() {
        assertEquals(
            listOf(Refusal.UnknownName(NamedList.ACTION, "SPEAK")),
            refusedFor(fileOf(weather.withCommand(weather.commands[0].copy(action = "SPEAK")))),
        )
        val fucha = accepted(weather.copy(permissions = listOf("internet", "call")))
        assertEquals(listOf(Permission.INTERNET, Permission.CALL), fucha.permissions)
        assertEquals(listOf(SentValue.CHOSEN_CITY), fucha.sends)
        assertEquals(Action.SPEAK, fucha.commands.single().action)
    }

    @Test
    fun an_accepted_fucha_carries_every_field_and_its_commands_know_where_they_came_from() {
        val fucha = accepted(weather)
        assertEquals("pogoda", fucha.id)
        assertEquals(1, fucha.version)
        assertEquals("Pogoda", fucha.name)
        assertEquals("MaksForge", fucha.author)
        assertEquals(listOf("https://api.open-meteo.com/v1/forecast"), fucha.addresses)
        val command = fucha.commands.single()
        assertEquals("pogoda", command.fucha)
        assertEquals(false, command.bundled)
        assertEquals(false, fucha.bundled)
        assertEquals(listOf("jaka przygoda"), fucha.decoys)
    }
}
