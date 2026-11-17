package app.darek.weather

import android.util.Log
import app.darek.fuchy.City
import app.darek.fuchy.Fucha
import app.darek.fuchy.Fuchy
import app.darek.fuchy.Permission
import app.darek.listen.Command
import app.darek.listen.Line
import app.darek.net.Address
import app.darek.net.Allowlist
import app.darek.net.HttpClient
import app.darek.net.HttpResult
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

sealed interface CitySearch {
    data class Found(
        val cities: List<City>,
    ) : CitySearch

    data object NoInternet : CitySearch

    data object Failed : CitySearch
}

/**
 * The `weather` action (phase-F.md section 5). Every request goes through a client that admits only
 * the fucha's declared addresses, and none goes out without the fucha's `internet` grant.
 */
class Weather(
    private val fuchy: Fuchy,
    private val clientFor: (Fucha) -> HttpClient = { fucha ->
        HttpClient(Allowlist(fucha.addresses.mapNotNull(Address::parse)))
    },
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    /** A 5-second limit in all, then the line saying so: nothing is retried in the background. */
    suspend fun reply(command: Command): Line {
        val (line, outcome) = forecastFor(command)
        log("forecast=$outcome")
        return line
    }

    private suspend fun forecastFor(command: Command): Pair<Line, String> {
        val installed = fuchy.find(command.fucha) ?: return WEATHER_FAILED to "not_installed"
        val city = installed.state?.city ?: return WEATHER_FAILED to "no_city"
        if (!fuchy.grantedTo(command.fucha, Permission.INTERNET)) return WEATHER_FAILED to "not_granted"
        return when (val result = fetch(installed.fucha, OpenMeteo.forecastUrl(city))) {
            is HttpResult.Response ->
                OpenMeteo.forecast(result.body)?.let { forecastLine(it) to "ok" }
                    ?: (WEATHER_FAILED to "unreadable")
            HttpResult.TimedOut, is HttpResult.Network -> WEATHER_NO_INTERNET to "no_internet"
            else -> WEATHER_FAILED to "failed $result"
        }
    }

    /** The city's name goes to the geocoding search, declared as *the city you chose*. */
    suspend fun search(
        fucha: Fucha,
        name: String,
    ): CitySearch {
        if (!fuchy.grantedTo(fucha.id, Permission.INTERNET)) return CitySearch.Failed
        return when (val result = fetch(fucha, OpenMeteo.searchUrl(name))) {
            is HttpResult.Response -> OpenMeteo.cities(result.body)?.let(CitySearch::Found) ?: CitySearch.Failed
            HttpResult.TimedOut, is HttpResult.Network -> CitySearch.NoInternet
            else -> CitySearch.Failed
        }
    }

    private suspend fun fetch(
        fucha: Fucha,
        url: String,
    ): HttpResult = withTimeoutOrNull(TIMEOUT) { clientFor(fucha).get(url, TIMEOUT, TIMEOUT) } ?: HttpResult.TimedOut

    private companion object {
        const val TAG = "DarekWeather"
        val TIMEOUT = 5.seconds
    }
}
