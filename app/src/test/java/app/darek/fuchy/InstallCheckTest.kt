package app.darek.fuchy

import app.darek.listen.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** phase-F.md, *Tests*: fails towards a fucha doing what its install screen did not show. */
class InstallCheckTest {
    private val speak = weather.commands[0]

    private fun refused(
        source: FuchaSource,
        bundled: Boolean = false,
        knows: (String) -> Boolean = knowsAll,
    ) = refusals(source, knows, bundled)

    @Test
    fun a_fucha_that_breaks_no_rule_is_accepted() {
        assertEquals(emptyList<Refusal>(), refused(weather))
        assertEquals(emptyList<Refusal>(), refused((parse(ownFuchaFile) as Parsed.Read).source, bundled = true))
    }

    @Test
    fun only_polish_is_accepted() {
        assertEquals(listOf(Refusal.WrongLanguage("en")), refused(weather.copy(language = "en")))
        assertEquals(listOf(Refusal.WrongLanguage("PL")), refused(weather.copy(language = "PL")))
    }

    @Test
    fun an_action_needs_its_permission_declared() {
        val call = speak.copy(id = "brama-dwa", action = "call")
        assertEquals(
            listOf(Refusal.Undeclared("brama-dwa", Permission.CALL)),
            refused(weather.withCommand(call), bundled = true),
        )
        assertEquals(
            listOf(Refusal.Undeclared("brama-dwa", Permission.CALL)),
            refused(weather.copy(permissions = emptyList()).withCommand(call), bundled = true),
        )
        assertEquals(
            emptyList<Refusal>(),
            refused(weather.copy(permissions = listOf("call")).withCommand(call), bundled = true),
        )
        assertEquals(emptyList<Refusal>(), refused(weather.copy(permissions = emptyList())))
    }

    @Test
    fun only_darek_s_own_fucha_may_call_a_number() {
        val call = speak.copy(id = "brama-dwa", action = "call")
        val withCall = weather.copy(permissions = listOf("call")).withCommand(call)
        assertEquals(listOf(Refusal.OwnOnly("brama-dwa", Action.CALL)), refused(withCall))
        assertTrue(refused(withCall).single() is Refusal.NeedsNewerDarek)
    }

    @Test
    fun every_action_this_darek_has_needs_a_reply() {
        listOf(null, "", "  ").forEach { reply ->
            assertEquals(
                listOf(Refusal.NoReply("jaka-pogoda")),
                refused(weather.withCommand(speak.copy(reply = reply))),
            )
        }
    }

    @Test
    fun a_command_with_no_words_of_its_own_is_refused() {
        listOf("", " ", "?!", "...").forEach { phrase ->
            assertEquals(
                phrase,
                listOf(Refusal.EmptyPhrase("jaka-pogoda")),
                refused(weather.withCommand(speak.copy(phrase = phrase))),
            )
        }
    }

    @Test
    fun addresses_are_https_to_an_exact_host_and_nothing_else() {
        listOf(
            "https://api.open-meteo.com",
            "https://api.open-meteo.com/",
            "https://api.open-meteo.com/v1/forecast",
            "https://API.open-meteo.com/v1/",
            "https://203.0.113.7/v1",
        ).forEach { assertTrue(it, isAllowedAddress(it)) }
        listOf(
            "http://api.open-meteo.com/v1/forecast",
            "HTTPS://api.open-meteo.com",
            "ftp://api.open-meteo.com",
            "https://*.open-meteo.com/v1",
            "https://api.open-meteo.com/v1?latitude=50",
            "https://api.open-meteo.com/v1?",
            "https://api.open-meteo.com/v1#top",
            "https://user@api.open-meteo.com/v1",
            "https://user:pass@api.open-meteo.com/v1",
            "https://api.open-meteo.com:443/v1",
            "https://api.open-meteo.com:8443/v1",
            "https:///v1/forecast",
            "https:api.open-meteo.com",
            "//api.open-meteo.com/v1",
            "api.open-meteo.com",
            "https://exa mple.com",
            "",
        ).forEach { assertFalse(it, isAllowedAddress(it)) }
        assertEquals(
            listOf(Refusal.BadAddress("http://api.open-meteo.com/v1/forecast")),
            refused(weather.copy(addresses = weather.addresses + "http://api.open-meteo.com/v1/forecast")),
        )
    }

    @Test
    fun a_phrase_holding_a_cancel_word_is_refused() {
        listOf("stop", "anuluj", "zatrzymaj stop", "Anuluj!", "anuluj zamówienie").forEach { phrase ->
            val found = refused(weather.withCommand(speak.copy(phrase = phrase)))
            assertEquals(phrase, 1, found.filterIsInstance<Refusal.CancelWord>().size)
        }
        assertEquals(emptyList<Refusal>(), refused(weather.withCommand(speak.copy(phrase = "stopa anulowana"))))
    }

    @Test
    fun every_phrase_and_decoy_word_must_be_known_to_the_model() {
        val known = setOf("jaka", "pogoda", "przygoda")
        assertEquals(emptyList<Refusal>(), refused(weather) { it in known })
        assertEquals(
            listOf(Refusal.UnknownWords(listOf("pogodzisko", "przygódka"))),
            refused(
                weather.withCommand(
                    speak.copy(phrase = "Jaka pogodzisko?", decoys = listOf("jaka przygódka", "pogoda")),
                ),
            ) { it in known },
        )
    }

    @Test
    fun the_vocabulary_check_asks_about_each_word_once_normalised() {
        val asked = mutableListOf<String>()
        refused(weather.withCommand(speak.copy(phrase = "Jaka,  POGODA", decoys = listOf("jaka przygoda")))) {
            asked += it
            true
        }
        assertEquals(listOf("jaka", "pogoda", "przygoda"), asked)
    }

    @Test
    fun every_refusal_is_reported_not_only_the_first() {
        val bad =
            weather.copy(
                language = "de",
                addresses = listOf("http://x.example"),
                commands = listOf(speak.copy(phrase = "stop pogoda", action = "call", reply = "Okej.")),
            )
        val found = refused(bad) { it != "pogoda" }
        assertEquals(
            setOf(
                Refusal.WrongLanguage("de"),
                Refusal.BadAddress("http://x.example"),
                Refusal.Undeclared("jaka-pogoda", Permission.CALL),
                Refusal.OwnOnly("jaka-pogoda", Action.CALL),
                Refusal.CancelWord("jaka-pogoda", "stop"),
                Refusal.UnknownWords(listOf("pogoda")),
            ),
            found.toSet(),
        )
        assertTrue(checked(fileOf(bad), knowsAll) is Checked.Refused)
    }

    @Test
    fun phrases_and_decoys_enter_the_grammar_normalised_and_the_reply_keeps_its_feminine_form() {
        val fucha =
            accepted(
                weather.withCommand(
                    speak.copy(
                        phrase = "  Jaka,   POGODA? ",
                        reply = "Sprawdziłem.",
                        replyFeminine = "Sprawdziłam.",
                        decoys = listOf("Jaka przygoda!"),
                    ),
                ),
            )
        val command = fucha.commands.single()
        assertEquals("jaka pogoda", command.phrase)
        assertEquals(listOf("jaka przygoda"), command.decoys)
        assertEquals("Sprawdziłem.", command.reply.by(Persona.DAREK))
        assertEquals("Sprawdziłam.", command.reply.by(Persona.DARIA))
        assertEquals(
            "Słonecznie.",
            accepted(weather)
                .commands
                .single()
                .reply
                .by(Persona.DARIA),
        )
    }
}
