package app.darek.net

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class HttpClientTest {
    private class FakeConnection(
        url: URL,
        private val status: Int,
        private val body: ByteArray = ByteArray(0),
        private val failure: IOException? = null,
    ) : HttpURLConnection(url) {
        var disconnected = false

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy() = false

        override fun getResponseCode(): Int = failure?.let { throw it } ?: status

        override fun getInputStream(): InputStream = ByteArrayInputStream(body)

        fun header(name: String): String? = getRequestProperty(name)
    }

    private val opened = mutableListOf<FakeConnection>()

    private fun client(
        status: Int = 200,
        body: ByteArray = ByteArray(0),
        failure: IOException? = null,
    ) = HttpClient(Allowlist.DAREK) { url -> FakeConnection(url, status, body, failure).also { opened += it } }

    private val fuchyJson = "https://maksforge.com/darek-fuchy/fuchy.json"

    @Test
    fun `a refused address never opens a connection`() =
        runTest {
            val result = client().get("https://maksforge.com.evil.example/darek-fuchy/fuchy.json")
            assertEquals(HttpResult.Refused(UrlProblem.NOT_DECLARED), result)
            assertTrue(opened.isEmpty())
        }

    @Test
    fun `plain http never opens a connection`() =
        runTest {
            assertEquals(
                HttpResult.Refused(UrlProblem.NOT_HTTPS),
                client().get("http://maksforge.com/darek-fuchy/fuchy.json"),
            )
            assertTrue(opened.isEmpty())
        }

    @Test
    fun `a redirect is a failure and is never followed`() =
        runTest {
            for (status in listOf(301, 302, 303, 307, 308)) {
                assertEquals(HttpResult.Redirected(status), client(status = status).get(fuchyJson))
            }
            assertTrue(opened.none { it.instanceFollowRedirects })
        }

    @Test
    fun `a success returns its status and body`() =
        runTest {
            val result = client(body = "{}".toByteArray()).get(fuchyJson)
            result as HttpResult.Response
            assertEquals(200, result.status)
            assertArrayEquals("{}".toByteArray(), result.body)
        }

    @Test
    fun `the connection goes to the checked host with the query intact`() =
        runTest {
            client().get("https://MAKSFORGE.com/darek-fuchy/fuchy.json?v=2")
            val url = opened.single().url
            assertEquals("https", url.protocol)
            assertEquals("maksforge.com", url.host)
            assertEquals("/darek-fuchy/fuchy.json?v=2", url.file)
        }

    @Test
    fun `a body over the cap is refused, one at the cap is not`() =
        runTest {
            assertEquals(HttpResult.TooLarge, client(body = ByteArray(11)).get(fuchyJson, maxBytes = 10))
            assertTrue(client(body = ByteArray(10)).get(fuchyJson, maxBytes = 10) is HttpResult.Response)
        }

    @Test
    fun `an error status is a failure`() =
        runTest {
            assertEquals(HttpResult.Status(404), client(status = 404).get(fuchyJson))
        }

    @Test
    fun `a timeout and a network error are told apart`() =
        runTest {
            assertEquals(HttpResult.TimedOut, client(failure = SocketTimeoutException()).get(fuchyJson))
            val failure = IOException("no route")
            assertEquals(HttpResult.Network(failure), client(failure = failure).get(fuchyJson))
        }

    @Test
    fun `timeouts default to five seconds and can be set`() =
        runTest {
            client().get(fuchyJson)
            client().get(fuchyJson, connectTimeout = 2.seconds, readTimeout = 3.seconds)
            assertEquals(listOf(5_000, 2_000), opened.map { it.connectTimeout })
            assertEquals(listOf(5_000, 3_000), opened.map { it.readTimeout })
        }

    @Test
    fun `the request is a plain GET that names no phone`() =
        runTest {
            client().get(fuchyJson)
            val connection = opened.single()
            assertEquals("GET", connection.requestMethod)
            assertEquals("Darek", connection.header("User-Agent"))
            assertFalse(connection.useCaches)
            assertTrue(connection.disconnected)
        }

    @Test
    fun `a timeout under a millisecond, infinite or past an Int of milliseconds is refused`() =
        runTest {
            for (timeout in listOf(
                Duration.ZERO,
                (-1).seconds,
                500.microseconds,
                Duration.INFINITE,
                (Int.MAX_VALUE + 1L).milliseconds,
            )) {
                assertThrows(
                    IllegalArgumentException::class.java,
                ) { runBlocking { client().get(fuchyJson, connectTimeout = timeout) } }
                assertThrows(
                    IllegalArgumentException::class.java,
                ) { runBlocking { client().get(fuchyJson, readTimeout = timeout) } }
            }
            assertTrue(opened.isEmpty())
            client().get(fuchyJson, connectTimeout = 1.milliseconds, readTimeout = 1.milliseconds)
            assertEquals(1, opened.single().readTimeout)
        }

    @Test
    fun `cancelling a request to a server that never answers returns at once and closes the socket`() =
        runBlocking {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                val closedByClient = CountDownLatch(1)
                thread(isDaemon = true) {
                    server.accept().use { socket ->
                        while (socket.getInputStream().read() != -1) Unit
                        closedByClient.countDown()
                    }
                }
                val silent = URL("http", server.inetAddress.hostAddress, server.localPort, "/")
                val client = HttpClient(Allowlist.DAREK) { silent.openConnection() as HttpURLConnection }

                val started = TimeSource.Monotonic.markNow()
                val result = withTimeoutOrNull(200.milliseconds) { client.get(fuchyJson, readTimeout = 60.seconds) }

                assertNull(result)
                assertTrue(started.elapsedNow() < 2.seconds)
                assertTrue(closedByClient.await(2, TimeUnit.SECONDS))
            }
        }
}
