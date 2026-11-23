package app.darek.fuchy

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.darek.data.AppPreferences
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** phase-F.md section 7 against a fake page: fails towards a silent widening, or a fresh install connecting. */
class FuchyPageTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val key = keyPair()
    private val served = mutableMapOf<String, ByteArray>()
    private val opened = mutableListOf<URL>()
    private var now = Instant.parse("2026-11-23T08:00:00Z")

    private inner class Fake(
        url: URL,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy() = false

        override fun getResponseCode(): Int = if (url.toString() in served) 200 else 404

        override fun getInputStream(): InputStream = ByteArrayInputStream(served.getValue(url.toString()))
    }

    private lateinit var preferences: AppPreferences
    private lateinit var dir: File
    private lateinit var fuchy: Fuchy
    private lateinit var page: FuchyPage

    private val fileUrl = "https://maksforge.com/darek-fuchy/fuchy/pogoda.darek"

    private fun publish(
        version: Int,
        file: ByteArray,
    ) {
        val listing =
            """{"id":"pogoda","version":$version,"name":"Pogoda","description":"Opis.","author":"MaksForge",""" +
                """"profanity":false,"actions":["weather"],"url":"$fileUrl"}"""
        served[PAGE_URL] = """{"format":1,"fuchy":[$listing]}""".toByteArray()
        served[fileUrl] = file
    }

    @Before
    fun setUp() {
        preferences =
            AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("p.preferences_pb") })
        dir = folder.newFolder("fuchy")
        start()
    }

    private fun start() =
        runBlocking {
            fuchy = Fuchy(dir, ownFucha, preferences, key.public)
            fuchy.load()
            page =
                FuchyPage(
                    fuchy,
                    preferences,
                    vocabulary = { knowsAll },
                    client = HttpClient(Allowlist.DAREK) { url -> Fake(url).also { opened += url } },
                    key = key.public,
                    now = { now },
                    log = {},
                )
        }

    private fun installV1() {
        File(dir, "pogoda.darek").writeBytes(signedFileOf(weatherAction, key))
        start()
        runBlocking { fuchy.switch(mapOf("pogoda" to true)) }
    }

    private fun version() = runBlocking { checkNotNull(fuchy.find("pogoda")).fucha.version }

    @After fun tearDown() = scope.cancel()

    @Test
    fun with_only_darek_s_own_nothing_connects() =
        runBlocking {
            publish(1, signedFileOf(weatherAction, key))
            assertEquals(UpdateCheck.NothingToUpdate, page.checkForUpdates())
            page.dailyCheck()
            assertEquals(emptyList<URL>(), opened)
        }

    @Test
    fun an_update_that_asks_for_nothing_more_installs_and_keeps_its_switch() =
        runBlocking {
            installV1()
            publish(2, signedFileOf(weatherAction.copy(version = 2), key))
            val check = page.checkForUpdates() as UpdateCheck.Done
            assertEquals(listOf("pogoda"), check.updated.updated.map { it.id })
            assertEquals(2, version())
            assertTrue(checkNotNull(fuchy.find("pogoda")).switchedOn)
        }

    @Test
    fun an_update_adding_an_address_waits_for_the_user() =
        runBlocking {
            installV1()
            val wider = weatherAction.copy(version = 2, addresses = weatherAction.addresses + "https://evil.example/x")
            publish(2, signedFileOf(wider, key))
            val check = page.checkForUpdates() as UpdateCheck.Done
            assertEquals(listOf("pogoda"), check.updated.waiting.map { it.id })
            assertEquals(1, version())
        }

    @Test
    fun an_unsigned_file_never_updates_an_approved_fucha() =
        runBlocking {
            installV1()
            publish(2, fileOf(weatherAction.copy(version = 2)))
            val check = page.checkForUpdates() as UpdateCheck.Done
            assertEquals(listOf("pogoda"), check.updated.waiting.map { it.id })
            assertEquals(1, version())
        }

    @Test
    fun a_file_that_is_not_what_its_listing_names_is_refused() =
        runBlocking {
            installV1()
            publish(1, signedFileOf(weatherAction.copy(id = "inna"), key))
            val listing = (page.read() as PageFetch.Listed).listings.single()
            assertEquals(Download.Failed, page.download(listing))
            publish(2, signedFileOf(weatherAction, key))
            assertEquals(Download.Failed, page.download((page.read() as PageFetch.Listed).listings.single()))
            publish(1, signedFileOf(weatherAction, key))
            assertTrue(page.download((page.read() as PageFetch.Listed).listings.single()) is Download.Got)
        }

    @Test
    fun a_file_no_newer_than_the_installed_one_is_not_installed() =
        runBlocking {
            installV1()
            publish(2, signedFileOf(weatherAction, key))
            val check = page.checkForUpdates() as UpdateCheck.Done
            assertEquals(listOf("pogoda"), check.updated.failed.map { it.id })
        }

    @Test
    fun the_daily_check_reads_the_page_at_most_once_a_day_and_not_when_turned_off() =
        runBlocking {
            installV1()
            publish(1, signedFileOf(weatherAction, key))
            page.dailyCheck()
            page.dailyCheck()
            assertEquals(1, opened.count { it.toString() == PAGE_URL })
            now = now.plusSeconds(25 * 3600)
            preferences.setAutoUpdate(false)
            page.dailyCheck()
            assertEquals(1, opened.count { it.toString() == PAGE_URL })
            preferences.setAutoUpdate(true)
            page.dailyCheck()
            assertEquals(2, opened.count { it.toString() == PAGE_URL })
            assertEquals(now, preferences.pageCheckedAt.first())
        }
}
