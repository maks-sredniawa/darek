package app.darek.weather

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.darek.data.AppPreferences
import app.darek.fuchy.City
import app.darek.fuchy.Fuchy
import app.darek.fuchy.Permission
import app.darek.fuchy.keyPair
import app.darek.fuchy.ownFucha
import app.darek.fuchy.signedFileOf
import app.darek.fuchy.weatherAction
import app.darek.listen.Command
import app.darek.net.Address
import app.darek.net.Allowlist
import app.darek.net.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** phase-F.md section 5: fails towards the city leaving the phone before the user allowed it. */
class WeatherTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val key = keyPair()
    private val opened = mutableListOf<URL>()
    private var failure: IOException? = null
    private val forecast =
        """{"current":{"temperature_2m":11.6,"weather_code":3},"daily":{"temperature_2m_max":[15.2],"temperature_2m_min":[7.8]}}"""

    private inner class Fake(
        url: URL,
    ) : HttpURLConnection(url) {
        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy() = false

        override fun getResponseCode(): Int = failure?.let { throw it } ?: 200

        override fun getInputStream(): InputStream = ByteArrayInputStream(forecast.toByteArray())
    }

    private lateinit var fuchy: Fuchy
    private lateinit var weather: Weather
    private lateinit var command: Command

    @Before
    fun setUp() =
        runBlocking {
            val preferences =
                AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("p.preferences_pb") })
            val dir = folder.newFolder("fuchy")
            File(dir, "pogoda.darek").writeBytes(signedFileOf(weatherAction, key))
            fuchy = Fuchy(dir, ownFucha, preferences, key.public)
            fuchy.load()
            command = checkNotNull(fuchy.find("pogoda")).fucha.commands.single()
            weather =
                Weather(
                    fuchy,
                    clientFor = { fucha ->
                        HttpClient(Allowlist(fucha.addresses.mapNotNull(Address::parse))) { url ->
                            Fake(url).also {
                                opened +=
                                    url
                            }
                        }
                    },
                    log = {},
                )
            fuchy.setCity("pogoda", City("Kraków", null, 50.06, 19.94))
            fuchy.switch(mapOf("pogoda" to true))
        }

    @After fun tearDown() = scope.cancel()

    @Test
    fun without_the_grant_nothing_is_sent() =
        runBlocking {
            assertEquals(WEATHER_FAILED, weather.reply(command))
            assertEquals(CitySearch.Failed, weather.search(checkNotNull(fuchy.find("pogoda")).fucha, "Kraków"))
            assertEquals(emptyList<URL>(), opened)
        }

    @Test
    fun with_the_grant_the_forecast_is_asked_for_the_chosen_city() =
        runBlocking {
            fuchy.grant("pogoda", listOf(Permission.INTERNET))
            assertEquals(forecastLine(Forecast(11.6, 3, 7.8, 15.2)), weather.reply(command))
            val url = opened.single()
            assertEquals("api.open-meteo.com", url.host)
            assertTrue(url.query, "latitude=50.0600&longitude=19.9400" in url.query)
        }

    @Test
    fun a_network_failure_says_there_is_no_internet() =
        runBlocking {
            fuchy.grant("pogoda", listOf(Permission.INTERNET))
            failure = IOException("offline")
            assertEquals(WEATHER_NO_INTERNET, weather.reply(command))
        }
}
