package app.darek.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface HttpResult {
    class Response(
        val status: Int,
        val body: ByteArray,
    ) : HttpResult

    sealed interface Failure : HttpResult

    data class Refused(
        val problem: UrlProblem,
    ) : Failure

    data class Redirected(
        val status: Int,
    ) : Failure

    data class Status(
        val status: Int,
    ) : Failure

    data object TooLarge : Failure

    data object TimedOut : Failure

    data class Network(
        val cause: IOException,
    ) : Failure
}

/** Every request Darek makes goes through one of these (ADR-0008); a fucha's carries only its own addresses. */
class HttpClient(
    private val allowlist: Allowlist,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    suspend fun get(
        url: String,
        connectTimeout: Duration = DEFAULT_TIMEOUT,
        readTimeout: Duration = DEFAULT_TIMEOUT,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): HttpResult {
        require(connectTimeout.isPositive() && readTimeout.isPositive()) { "HttpURLConnection reads 0 as no timeout" }
        val admitted =
            when (val inspection = allowlist.admit(url)) {
                is Inspection.Refused -> return HttpResult.Refused(inspection.problem)
                is Inspection.Clean -> inspection
            }
        // From the checked parts, not re-parsed: java.net.URL and URI may not agree on a host.
        val file = admitted.rawPath + admitted.rawQuery?.let { "?$it" }.orEmpty()
        val target = URL("https", admitted.host, file)
        return withContext(Dispatchers.IO) { fetch(target, connectTimeout, readTimeout, maxBytes) }
    }

    private fun fetch(
        url: URL,
        connectTimeout: Duration,
        readTimeout: Duration,
        maxBytes: Int,
    ): HttpResult {
        val connection =
            try {
                open(url)
            } catch (e: IOException) {
                return HttpResult.Network(e)
            }
        return try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = connectTimeout.inWholeMilliseconds.toInt()
            connection.readTimeout = readTimeout.inWholeMilliseconds.toInt()
            connection.requestMethod = "GET"
            // The platform's default names the phone's model and Android version (ADR-0008).
            connection.setRequestProperty("User-Agent", USER_AGENT)
            when (val status = connection.responseCode) {
                in 200..299 -> {
                    val body = connection.inputStream.use { it.readAtMost(maxBytes) }
                    if (body == null) HttpResult.TooLarge else HttpResult.Response(status, body)
                }
                in 300..399 -> HttpResult.Redirected(status)
                else -> HttpResult.Status(status)
            }
        } catch (_: SocketTimeoutException) {
            HttpResult.TimedOut
        } catch (e: IOException) {
            HttpResult.Network(e)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        val DEFAULT_TIMEOUT = 5.seconds
        const val DEFAULT_MAX_BYTES = 512 * 1024
        private const val USER_AGENT = "Darek"
    }
}

private fun InputStream.readAtMost(maxBytes: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (true) {
        val read = read(buffer)
        if (read == -1) return out.toByteArray()
        if (out.size() + read > maxBytes) return null
        out.write(buffer, 0, read)
    }
}
