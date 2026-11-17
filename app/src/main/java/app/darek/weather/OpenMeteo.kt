package app.darek.weather

import app.darek.fuchy.City
import app.darek.listen.Line
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/** Open-Meteo, which needs no key (phase-F.md section 5). Pure, so the URLs and the Reply have tests. */
object OpenMeteo {
    const val GEOCODING = "https://geocoding-api.open-meteo.com/v1/search"
    const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    const val MAX_CITIES = 10

    private val json = Json { ignoreUnknownKeys = true }

    fun searchUrl(name: String): String =
        "$GEOCODING?name=${URLEncoder.encode(name.trim(), "UTF-8")}&count=$MAX_CITIES&language=pl&format=json"

    fun forecastUrl(city: City): String =
        "$FORECAST?latitude=${city.latitude.coordinate()}&longitude=${city.longitude.coordinate()}" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"

    /** Null when the body is not Open-Meteo's answer; an empty list when it found nothing. */
    fun cities(body: ByteArray): List<City>? =
        runCatching { json.decodeFromString<Places>(body.decodeToString()) }.getOrNull()?.results?.map {
            City(
                it.name,
                listOfNotNull(it.admin1, it.country).joinToString(", ").ifEmpty { null },
                it.latitude,
                it.longitude,
            )
        }

    fun forecast(body: ByteArray): Forecast? =
        runCatching { json.decodeFromString<ForecastBody>(body.decodeToString()) }.getOrNull()?.let {
            Forecast(
                now = it.current.temperature,
                code = it.current.weatherCode,
                low = it.daily.low.firstOrNull() ?: return null,
                high = it.daily.high.firstOrNull() ?: return null,
            )
        }

    private fun Double.coordinate(): String = String.format(Locale.ROOT, "%.4f", this)

    @Serializable
    private class Places(
        val results: List<Place> = emptyList(),
    )

    @Serializable
    private class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val admin1: String? = null,
        val country: String? = null,
    )

    @Serializable
    private class ForecastBody(
        val current: Current,
        val daily: Daily,
    )

    @Serializable
    private class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int? = null,
    )

    @Serializable
    private class Daily(
        @SerialName("temperature_2m_min") val low: List<Double>,
        @SerialName("temperature_2m_max") val high: List<Double>,
    )
}

data class Forecast(
    val now: Double,
    val code: Int?,
    val low: Double,
    val high: Double,
)

/**
 * *„Teraz 12°, pochmurno. Dziś od 8 do 15°."* The degree sign is spelt out by the voice with the
 * right form of *stopień*. No city: it is typed in its nominative, and *„w Krakowie"* needs its locative.
 */
fun forecastLine(forecast: Forecast): Line {
    val sky =
        forecast.code
            ?.let(::weatherWords)
            ?.let { ", $it" }
            .orEmpty()
    return Line(
        "Teraz ${forecast.now.roundToInt()}°$sky. Dziś od ${forecast.low.roundToInt()} do ${forecast.high.roundToInt()}°.",
    )
}

/** WMO weather interpretation codes, as Open-Meteo documents them; an unknown code says nothing about the sky. */
fun weatherWords(code: Int): String? =
    when (code) {
        0 -> "bezchmurnie"
        1 -> "prawie bezchmurnie"
        2 -> "trochę chmur"
        3 -> "pochmurno"
        45, 48 -> "mgła"
        51, 53, 55 -> "mżawka"
        56, 57 -> "marznąca mżawka"
        61 -> "słaby deszcz"
        63 -> "deszcz"
        65 -> "ulewa"
        66, 67 -> "marznący deszcz"
        71 -> "słaby śnieg"
        73 -> "śnieg"
        75 -> "mocny śnieg"
        77 -> "drobny śnieg"
        80 -> "przelotny deszcz"
        81 -> "przelotne opady deszczu"
        82 -> "ulewy"
        85, 86 -> "przelotny śnieg"
        95 -> "burza"
        96, 99 -> "burza z gradem"
        else -> null
    }

val WEATHER_NO_INTERNET = Line("Nie mogę sprawdzić pogody, nie mam internetu.")

val WEATHER_FAILED = Line("Nie mogę teraz sprawdzić pogody.")
