package app.darek.fuchy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** phase-F.md, *Tests*: fails towards a silent widening of what a fucha may do. */
class CatalogueTest {
    private val page =
        """
        {"format":1,"fuchy":[{"id":"pogoda","version":1,"name":"Pogoda","description":"Pogoda.","author":"MaksForge",
        "profanity":false,"actions":["weather"],"url":"https://maksforge.com/darek-fuchy/fuchy/pogoda.darek","extra":1}]}
        """.trimIndent()

    @Test
    fun the_page_darek_fuchy_publishes_is_read() {
        val read = parseCatalogue(page.toByteArray()) as Catalogue.Read
        assertEquals("pogoda", read.entries.single().id)
        assertFalse(read.entries.single().needsNewerDarek)
    }

    @Test
    fun a_newer_page_needs_a_newer_darek_and_a_broken_one_is_unreadable() {
        assertEquals(Catalogue.NewerFormat(2), parseCatalogue("""{"format":2,"fuchy":"anything"}""".toByteArray()))
        assertEquals(Catalogue.Unreadable, parseCatalogue("<html>".toByteArray()))
        assertEquals(Catalogue.Unreadable, parseCatalogue("""{"fuchy":[]}""".toByteArray()))
    }

    @Test
    fun an_unknown_action_or_one_kept_for_darek_s_own_needs_a_newer_darek() {
        val entry = (parseCatalogue(page.toByteArray()) as Catalogue.Read).entries.single()
        listOf("navigate", "call", "update_fuchy").forEach {
            assertTrue(it, entry.copy(actions = listOf("speak", it)).needsNewerDarek)
        }
    }

    private val maksForge = keyPair()

    private fun version(
        version: Int,
        source: FuchaSource = weatherAction,
        signed: Boolean = true,
    ): Fucha {
        val file =
            if (signed) {
                signedFileOf(
                    source.copy(version = version),
                    maksForge,
                )
            } else {
                fileOf(source.copy(version = version))
            }
        return (checked(file, knowsAll, key = maksForge.public) as Checked.Accepted).fucha
    }

    private fun decide(new: Fucha) =
        updateDecision(
            planInstall(new, listOf(Installed(ownFucha, true), Installed(version(1), true, FuchaState(true)))),
        )

    @Test
    fun an_update_that_declares_nothing_new_installs_by_itself() {
        assertEquals(UpdateDecision.Automatic, decide(version(2)))
    }

    @Test
    fun an_update_widening_what_a_fucha_may_do_waits_for_the_user() {
        val moreAddresses = weatherAction.copy(addresses = weatherAction.addresses + "https://example.com/v1")
        assertEquals(UpdateDecision.Waits(listOf(WaitsBecause.NEW_ADDRESS)), decide(version(2, moreAddresses)))
        val morePermissions = weatherAction.copy(permissions = listOf("internet", "call"))
        assertEquals(UpdateDecision.Waits(listOf(WaitsBecause.NEW_PERMISSION)), decide(version(2, morePermissions)))
        assertEquals(UpdateDecision.Waits(listOf(WaitsBecause.UNAPPROVED)), decide(version(2, signed = false)))
    }

    @Test
    fun an_update_that_clashes_waits_for_the_user() {
        val clashing =
            weatherAction.copy(
                commands =
                    weatherAction.commands +
                        CommandSource(id = "brama", phrase = "otwórz bramę", action = "speak", reply = "Nie."),
            )
        assertEquals(UpdateDecision.Waits(listOf(WaitsBecause.CLASH)), decide(version(2, clashing)))
    }
}
