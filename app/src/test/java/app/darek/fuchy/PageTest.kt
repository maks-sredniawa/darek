package app.darek.fuchy

import app.darek.listen.Persona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** phase-F.md, *Tests*: the update decision fails towards a silent widening of what a fucha may do. */
class PageTest {
    private fun listing(
        id: String = "pogoda",
        version: Int = 1,
        profanity: Boolean = false,
        actions: List<String> = listOf("weather"),
        url: String = "https://maksforge.com/darek-fuchy/fuchy/$id.darek",
    ) = Listing(id, version, id.replaceFirstChar { it.uppercase() }, "Opis.", "MaksForge", profanity, actions, url)

    private fun fucha(
        version: Int = 1,
        approved: Boolean = true,
        permissions: List<Permission> = listOf(Permission.INTERNET),
        addresses: List<String> = listOf("https://api.open-meteo.com/v1/forecast"),
        sends: List<SentValue> = listOf(SentValue.CHOSEN_CITY),
        id: String = "pogoda",
    ) = accepted(weather).copy(
        id = id,
        version = version,
        approved = approved,
        permissions = permissions,
        addresses = addresses,
        sends = sends,
    )

    private fun page(vararg listings: Listing): ByteArray {
        val items =
            listings.joinToString(",") {
                """{"id":"${it.id}","version":${it.version},"name":"${it.name}","description":"${it.description}",""" +
                    """"author":"${it.author}","profanity":${it.profanity},""" +
                    """"actions":[${it.actions.joinToString(",") { a -> "\"$a\"" }}],"url":"${it.url}"}"""
            }
        return """{"format":1,"fuchy":[$items]}""".toByteArray()
    }

    @Test
    fun the_published_page_is_read() {
        val read = readPage(page(listing()))
        assertEquals(PageRead.Listed(listOf(listing())), read)
    }

    @Test
    fun a_listing_whose_file_is_off_the_fuchy_page_is_dropped() {
        val elsewhere =
            listOf(
                "https://evil.example/darek-fuchy/fuchy/pogoda.darek",
                "http://maksforge.com/darek-fuchy/fuchy/pogoda.darek",
                "https://maksforge.com/other/pogoda.darek",
                "https://maksforge.com.example/darek-fuchy/pogoda.darek",
            )
        elsewhere.forEach { url ->
            assertEquals(url, PageRead.Listed(emptyList()), readPage(page(listing(url = url))))
        }
    }

    @Test
    fun a_later_format_needs_a_newer_darek_and_garbage_is_unreadable() {
        assertEquals(PageRead.NewerFormat, readPage("""{"format":2,"fuchy":[]}""".toByteArray()))
        assertEquals(PageRead.Unreadable, readPage("nie json".toByteArray()))
        assertEquals(PageRead.Unreadable, readPage("""{"fuchy":[]}""".toByteArray()))
        assertEquals(PageRead.Unreadable, readPage(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    @Test
    fun installed_fuchy_come_first_and_darek_own_is_never_listed() {
        val rows =
            pageRows(
                listOf(listing("zarty", actions = listOf("speak")), listing(OwnFucha.ID), listing()),
                listOf(Installed(fucha(), on = true)),
                showProfanity = false,
            )
        assertEquals(listOf("pogoda", "zarty"), rows.map { it.listing.id })
        assertEquals(listOf(Offer.UpToDate, Offer.Install), rows.map { it.offer })
    }

    @Test
    fun an_unknown_or_own_only_action_needs_a_newer_darek() {
        val rows =
            pageRows(
                listOf(listing("a", actions = listOf("teleport")), listing("b", actions = listOf("call"))),
                emptyList(),
                showProfanity = false,
            )
        assertEquals(listOf(Offer.NeedsNewerDarek, Offer.NeedsNewerDarek), rows.map { it.offer })
    }

    @Test
    fun profanity_is_hidden_until_shown_unless_already_installed() {
        val listings = listOf(listing("brzydkie", profanity = true), listing(profanity = true))
        val installed = listOf(Installed(fucha(), on = true))
        assertEquals(listOf("pogoda"), pageRows(listings, installed, showProfanity = false).map { it.listing.id })
        assertEquals(2, pageRows(listings, installed, showProfanity = true).size)
    }

    @Test
    fun only_an_approved_fucha_is_offered_the_page_copy_as_an_update() {
        val newer = listOf(listing(version = 2))
        assertEquals(Offer.Update(1), pageRows(newer, listOf(Installed(fucha(), true)), false).single().offer)
        assertEquals(
            Offer.Install,
            pageRows(newer, listOf(Installed(fucha(approved = false), true)), false).single().offer,
        )
    }

    @Test
    fun an_update_that_widens_what_the_fucha_may_do_waits() {
        val old = fucha()
        assertFalse(updateWaits(old, fucha(version = 2)))
        assertFalse(updateWaits(old, fucha(version = 2, permissions = emptyList(), sends = emptyList())))
        assertTrue(updateWaits(old, fucha(version = 2, addresses = old.addresses + "https://evil.example/x")))
        assertTrue(updateWaits(old.copy(permissions = emptyList()), fucha(version = 2)))
        assertTrue(updateWaits(old.copy(sends = emptyList()), fucha(version = 2)))
        assertTrue(updateWaits(old, fucha(version = 2, approved = false)))
    }

    @Test
    fun the_spoken_outcome_names_each_fucha_in_both_genders() {
        val done = UpdateCheck.Done(Updated(updated = listOf(fucha()), waiting = listOf(listing("zarty"))))
        assertEquals(
            "Zaktualizowałam: Pogoda. Na twoją zgodę na stronie z fuchami czeka: Zarty.",
            lineFor(done).by(Persona.DARIA),
        )
        assertEquals(ALL_UP_TO_DATE, lineFor(UpdateCheck.Done(Updated())))
        assertEquals(NOTHING_TO_UPDATE, lineFor(UpdateCheck.NothingToUpdate))
        assertEquals(UPDATE_NO_INTERNET, lineFor(UpdateCheck.NotRead(PageFetch.NoInternet)))
        assertEquals(UPDATE_FAILED, lineFor(UpdateCheck.NotRead(PageFetch.Failed)))
    }
}
