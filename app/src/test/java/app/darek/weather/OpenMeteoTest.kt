package app.darek.weather

import app.darek.fuchy.City
import app.darek.fuchy.accepted
import app.darek.fuchy.weatherAction
import app.darek.listen.Line
import app.darek.net.Address
import app.darek.net.Allowlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoTest {
    private val krakow = City("Kraków", "Małopolskie, Polska", 50.0614, 19.9366)
    private val declared = Allowlist(accepted(weatherAction).addresses.mapNotNull(Address::parse))

    @Test
    fun both_requests_go_only_to_the_addresses_the_weather_fucha_declares() {
        assertNull(declared.refusalOf(OpenMeteo.searchUrl("Kraków")))
        assertNull(declared.refusalOf(OpenMeteo.forecastUrl(krakow)))
    }

    @Test
    fun the_city_name_is_encoded_and_the_coordinates_never_follow_the_locale() {
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=Bielsko-Bia%C5%82a&count=10&language=pl&format=json",
            OpenMeteo.searchUrl(" Bielsko-Biała "),
        )
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=Nowy+Targ&count=10&language=pl&format=json",
            OpenMeteo.searchUrl("Nowy Targ"),
        )
        val url = OpenMeteo.forecastUrl(krakow)
        assert("latitude=50.0614&longitude=19.9366&" in url) { url }
    }

    @Test
    fun cities_are_read_with_their_region_and_none_found_is_an_empty_list() {
        val body =
            """
            {"results":[{"id":1,"name":"Kraków","latitude":50.06143,"longitude":19.93658,"admin1":"Małopolskie",
            "country":"Polska","population":755050},{"name":"Krakow","latitude":44.6,"longitude":-89.1}],"generationtime_ms":0.5}
            """.trimIndent().toByteArray()
        assertEquals(
            listOf(City("Kraków", "Małopolskie, Polska", 50.06143, 19.93658), City("Krakow", null, 44.6, -89.1)),
            OpenMeteo.cities(body),
        )
        assertEquals(emptyList<City>(), OpenMeteo.cities("""{"generationtime_ms":0.3}""".toByteArray()))
        assertNull(OpenMeteo.cities("<html>".toByteArray()))
    }

    @Test
    fun the_forecast_is_read_from_current_and_today() {
        val body =
            """
            {"latitude":50.06,"current":{"time":"2026-11-17T14:00","temperature_2m":11.6,"weather_code":3},
            "daily":{"time":["2026-11-17"],"temperature_2m_max":[15.2],"temperature_2m_min":[7.8]}}
            """.trimIndent().toByteArray()
        assertEquals(Forecast(now = 11.6, code = 3, low = 7.8, high = 15.2), OpenMeteo.forecast(body))
        assertNull(
            OpenMeteo.forecast(
                """{"current":{"temperature_2m":1.0},"daily":{"temperature_2m_max":[],"temperature_2m_min":[]}}"""
                    .toByteArray(),
            ),
        )
        assertNull(OpenMeteo.forecast("nope".toByteArray()))
    }

    @Test
    fun the_reply_rounds_and_names_the_sky_in_polish() {
        assertEquals(
            Line("Teraz 12°, pochmurno. Dziś od 8 do 15°."),
            forecastLine(Forecast(now = 11.6, code = 3, low = 7.8, high = 15.2)),
        )
        assertEquals(
            Line("Teraz -3°, śnieg. Dziś od -5 do 0°."),
            forecastLine(Forecast(now = -2.6, code = 73, low = -5.2, high = -0.4)),
        )
        assertEquals(Line("Teraz 20°. Dziś od 14 do 24°."), forecastLine(Forecast(20.0, 42, 14.0, 24.0)))
        assertEquals(Line("Teraz 20°. Dziś od 14 do 24°."), forecastLine(Forecast(20.0, null, 14.0, 24.0)))
    }
}
