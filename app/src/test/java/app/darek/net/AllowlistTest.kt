package app.darek.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AllowlistTest {
    private val weather =
        Allowlist(
            listOf(
                address("https://api.open-meteo.com/v1/forecast"),
                address("https://geocoding-api.open-meteo.com/v1/search"),
            ),
        )

    private fun address(declared: String) = requireNotNull(Address.parse(declared)) { declared }

    @Test
    fun `a declared address with a query is admitted`() {
        assertNull(weather.refusalOf("https://api.open-meteo.com/v1/forecast?latitude=50.06&longitude=19.94"))
    }

    @Test
    fun `plain http is refused`() {
        assertEquals(UrlProblem.NOT_HTTPS, weather.refusalOf("http://api.open-meteo.com/v1/forecast"))
    }

    @Test
    fun `a host that only ends with the declared one is refused`() {
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://evilapi.open-meteo.com/v1/forecast"))
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://evil.api.open-meteo.com/v1/forecast"))
    }

    @Test
    fun `a host that only starts with the declared one is refused`() {
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com.evil.example/v1/forecast"))
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://evilapi.open-meteo.com.example/v1/forecast"))
    }

    @Test
    fun `a trailing dot on the host is a different host`() {
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com./v1/forecast"))
    }

    @Test
    fun `hosts compare without case, on both sides`() {
        assertNull(weather.refusalOf("https://API.Open-Meteo.COM/v1/forecast"))
        assertNull(
            Allowlist(
                listOf(address("https://API.OPEN-METEO.com/v1/forecast")),
            ).refusalOf("https://api.open-meteo.com/v1/forecast"),
        )
    }

    @Test
    fun `paths compare with case`() {
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com/V1/Forecast"))
    }

    @Test
    fun `a path outside the prefix is refused`() {
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com/v1/archive"))
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com/"))
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com"))
    }

    @Test
    fun `the prefix ends on a segment boundary`() {
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com/v1/forecastX"))
        assertNull(weather.refusalOf("https://api.open-meteo.com/v1/forecast/daily"))
        assertNull(weather.refusalOf("https://api.open-meteo.com/v1/forecast/"))
    }

    @Test
    fun `an address declared with a trailing slash also admits the bare path`() {
        assertNull(Allowlist.DAREK.refusalOf("https://maksforge.com/darek-fuchy"))
        assertNull(Allowlist.DAREK.refusalOf("https://maksforge.com/darek-fuchy/fuchy.json"))
        assertEquals(
            UrlProblem.NOT_DECLARED,
            Allowlist.DAREK.refusalOf("https://maksforge.com/darek-fuchyX/fuchy.json"),
        )
    }

    @Test
    fun `an address with no path admits the whole host and no other`() {
        val wholeHost = Allowlist(listOf(address("https://example.com")))
        assertNull(wholeHost.refusalOf("https://example.com/anything/at/all?q=1"))
        assertNull(wholeHost.refusalOf("https://example.com"))
        assertEquals(UrlProblem.NOT_DECLARED, wholeHost.refusalOf("https://www.example.com/"))
    }

    @Test
    fun `a path climbing out of the prefix is refused, encoded or not`() {
        assertEquals(UrlProblem.PATH_TRAVERSAL, weather.refusalOf("https://api.open-meteo.com/v1/forecast/../../admin"))
        assertEquals(
            UrlProblem.PATH_TRAVERSAL,
            weather.refusalOf("https://api.open-meteo.com/v1/forecast/%2e%2e/admin"),
        )
        assertEquals(
            UrlProblem.PATH_TRAVERSAL,
            weather.refusalOf("https://api.open-meteo.com/v1/forecast/x%2F..%2F..%2Fadmin"),
        )
        assertEquals(UrlProblem.PATH_TRAVERSAL, weather.refusalOf("https://api.open-meteo.com/v1/forecast/./x"))
        assertEquals(
            UrlProblem.PATH_TRAVERSAL,
            weather.refusalOf("https://api.open-meteo.com/v1/forecast/%5C..%5Cadmin"),
        )
    }

    @Test
    fun `an encoded character in the prefix does not count as the prefix`() {
        assertEquals(UrlProblem.NOT_DECLARED, weather.refusalOf("https://api.open-meteo.com/v1/forecas%74"))
    }

    @Test
    fun `userinfo in a request is refused`() {
        assertEquals(UrlProblem.USERINFO, weather.refusalOf("https://api.open-meteo.com@evil.example/v1/forecast"))
        assertEquals(UrlProblem.USERINFO, weather.refusalOf("https://user:pass@api.open-meteo.com/v1/forecast"))
    }

    @Test
    fun `an explicit port in a request is refused, even the default one`() {
        assertEquals(UrlProblem.EXPLICIT_PORT, weather.refusalOf("https://api.open-meteo.com:443/v1/forecast"))
        assertEquals(UrlProblem.EXPLICIT_PORT, weather.refusalOf("https://api.open-meteo.com:8443/v1/forecast"))
    }

    @Test
    fun `a fragment in a request is refused`() {
        assertEquals(UrlProblem.FRAGMENT, weather.refusalOf("https://api.open-meteo.com/v1/forecast#x"))
    }

    @Test
    fun `garbage and relative urls are refused`() {
        assertEquals(UrlProblem.MALFORMED, weather.refusalOf("not a url"))
        assertEquals(UrlProblem.MALFORMED, weather.refusalOf("/v1/forecast"))
        assertEquals(UrlProblem.MALFORMED, weather.refusalOf("https:///v1/forecast"))
        assertEquals(UrlProblem.MALFORMED, weather.refusalOf("https://evil.example\\@api.open-meteo.com/v1/forecast"))
    }

    @Test
    fun `nothing is admitted by an empty allowlist`() {
        assertEquals(
            UrlProblem.NOT_DECLARED,
            Allowlist(emptyList()).refusalOf("https://maksforge.com/darek-fuchy/fuchy.json"),
        )
    }

    @Test
    fun `darek reaches only the fuchy page on maksforge`() {
        assertEquals(UrlProblem.NOT_DECLARED, Allowlist.DAREK.refusalOf("https://maksforge.com/"))
        assertEquals(UrlProblem.NOT_DECLARED, Allowlist.DAREK.refusalOf("https://maksforge.com/darek/"))
        assertEquals(
            UrlProblem.NOT_DECLARED,
            Allowlist.DAREK.refusalOf("https://www.maksforge.com/darek-fuchy/fuchy.json"),
        )
        assertEquals(UrlProblem.NOT_DECLARED, Allowlist.DAREK.refusalOf("https://api.open-meteo.com/v1/forecast"))
    }

    @Test
    fun `a declared address carries no userinfo, port, query or fragment`() {
        assertEquals(UrlProblem.USERINFO, Address.problemWith("https://me@api.open-meteo.com/v1/forecast"))
        assertEquals(UrlProblem.EXPLICIT_PORT, Address.problemWith("https://api.open-meteo.com:443/v1/forecast"))
        assertEquals(UrlProblem.QUERY, Address.problemWith("https://api.open-meteo.com/v1/forecast?latitude=1"))
        assertEquals(UrlProblem.QUERY, Address.problemWith("https://api.open-meteo.com/v1/forecast?"))
        assertEquals(UrlProblem.FRAGMENT, Address.problemWith("https://api.open-meteo.com/v1/forecast#x"))
        assertEquals(UrlProblem.NOT_HTTPS, Address.problemWith("http://api.open-meteo.com/v1/forecast"))
        assertEquals(UrlProblem.PATH_TRAVERSAL, Address.problemWith("https://api.open-meteo.com/v1/../admin"))
        assertNull(Address.parse("https://api.open-meteo.com/v1/forecast?latitude=1"))
    }

    @Test
    fun `a clean declared address has no problem`() {
        assertNull(Address.problemWith("https://api.open-meteo.com/v1/forecast"))
        assertNotNull(Address.parse("https://api.open-meteo.com/v1/forecast"))
    }
}
