package app.darek.fuchy

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.darek.data.AppPreferences
import app.darek.listen.Line
import app.darek.listen.Persona
import app.darek.net.Allowlist
import app.darek.net.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** phase-F.md, *Tests*: fails towards a silent widening of what a fucha may do, or a connection nobody asked for. */
class UpdatesTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val key = keyPair()
    private val served = mutableMapOf<String, ByteArray>()
    private val opened = mutableListOf<String>()
    private val city = City("Kraków", null, 50.06, 19.94)
    private var clock = Instant.parse("2026-11-23T12:00:00Z")

    private inner class Fake(
        url: URL,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy() = false

        override fun getResponseCode(): Int = if (url.toString() in served) 200 else 404

        override fun getInputStream(): InputStream = ByteArrayInputStream(served.getValue(url.toString()))
    }

    private val preferences by lazy {
        AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("p.preferences_pb") })
    }
    private val dir by lazy { folder.newFolder("fuchy") }
    private val fuchy by lazy { Fuchy(dir, ownFucha, preferences, key.public) }
    private val updates by lazy {
        FuchyUpdates(
            fuchy,
            words = { knowsAll },
            preferences = preferences,
            client = HttpClient(Allowlist.DAREK) { url -> Fake(url).also { opened += url.toString() } },
            now = { clock },
            log = {},
            key = key.public,
        )
    }

    @After fun tearDown() = scope.cancel()

    private fun publish(source: FuchaSource) {
        val url = "https://maksforge.com/darek-fuchy/fuchy/${source.id}.darek"
        served[url] = signedFileOf(source, key)
        served[CATALOGUE_URL] =
            """{"format":1,"fuchy":[{"id":"${source.id}","version":${source.version},"name":"${source.name}",
            "description":"d","author":"MaksForge","actions":["weather"],"url":"$url"}]}""".toByteArray()
    }

    private suspend fun installPogoda() {
        File(dir, "pogoda.darek").writeBytes(signedFileOf(weatherAction, key))
        fuchy.load()
        fuchy.grant("pogoda", listOf(Permission.INTERNET))
        fuchy.setCity("pogoda", city)
        fuchy.switch(mapOf("pogoda" to true))
    }

    @Test
    fun with_only_darek_s_own_nothing_connects() =
        runBlocking {
            fuchy.load()
            assertEquals(UpdateRun.NothingToUpdate, updates.run(asked = true))
            updates.daily()
            assertEquals(emptyList<String>(), opened)
        }

    @Test
    fun an_update_declaring_nothing_new_installs_and_keeps_the_grant_the_city_and_the_switch() =
        runBlocking {
            installPogoda()
            publish(weatherAction.copy(version = 2))
            val run = updates.run(asked = false) as UpdateRun.Done
            assertEquals(listOf(2), run.updated.map { it.version })
            val now = fuchy.find("pogoda")!!
            assertEquals(2, now.fucha.version)
            assertTrue(now.on)
            assertEquals(city, now.state?.city)
        }

    @Test
    fun an_update_with_a_new_address_waits_and_nothing_is_installed() =
        runBlocking {
            installPogoda()
            publish(weatherAction.copy(version = 2, addresses = weatherAction.addresses + "https://example.com/v1"))
            assertEquals(UpdateRun.Done(emptyList(), listOf(fuchy.find("pogoda")!!.fucha)), updates.run(asked = true))
            assertEquals(1, fuchy.find("pogoda")!!.fucha.version)
        }

    @Test
    fun with_automatic_updates_off_only_asking_installs() =
        runBlocking {
            installPogoda()
            preferences.setFuchyAutoUpdate(false)
            publish(weatherAction.copy(version = 2))
            assertEquals(1, (updates.run(asked = false) as UpdateRun.Done).waiting.size)
            assertEquals(1, (updates.run(asked = true) as UpdateRun.Done).updated.size)
        }

    @Test
    fun the_daily_check_reads_the_page_at_most_once_a_day() =
        runBlocking {
            installPogoda()
            publish(weatherAction)
            updates.daily()
            assertEquals(listOf(CATALOGUE_URL), opened)
            clock = clock.plusSeconds(23 * 3600)
            updates.daily()
            assertEquals(1, opened.size)
            clock = clock.plusSeconds(3600)
            updates.daily()
            assertEquals(2, opened.size)
            assertEquals(clock, preferences.fuchyCheckedAt.first())
        }

    @Test
    fun the_spoken_line_says_what_was_done_in_each_persona() {
        val pogoda = accepted(weatherAction)
        assertEquals(Line("Wszystkie fuchy są aktualne."), updateLine(UpdateRun.Done(emptyList(), emptyList())))
        assertEquals(
            "Zaktualizowałam: Pogoda.",
            updateLine(UpdateRun.Done(listOf(pogoda), emptyList())).by(Persona.DARIA),
        )
        assertEquals(
            "Na twoją zgodę w aplikacji czeka: Pogoda.",
            updateLine(UpdateRun.Done(emptyList(), listOf(pogoda))).by(Persona.DAREK),
        )
        assertEquals(Line("Nie mam żadnej fuchy do aktualizowania."), updateLine(UpdateRun.NothingToUpdate))
    }
}
