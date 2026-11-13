package app.darek.net

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

enum class UrlProblem {
    MALFORMED,
    NOT_HTTPS,
    USERINFO,
    EXPLICIT_PORT,
    QUERY,
    FRAGMENT,
    PATH_TRAVERSAL,
    NOT_DECLARED,
}

/**
 * An address a fucha declares, or one of Darek's own: an exact host, and a path prefix matched on whole
 * segments, so `/v1/forecast` admits `/v1/forecast/daily` but never `/v1/forecastX`. No path admits
 * the whole host.
 */
class Address private constructor(
    val host: String,
    private val path: String,
) {
    internal fun admits(
        host: String,
        path: String,
    ): Boolean = host == this.host && (path == this.path || path.startsWith("${this.path}/"))

    override fun toString(): String = "https://$host$path"

    companion object {
        fun problemWith(declared: String): UrlProblem? =
            (inspect(declared, isDeclared = true) as? Inspection.Refused)?.problem

        fun parse(declared: String): Address? {
            val clean = inspect(declared, isDeclared = true) as? Inspection.Clean ?: return null
            return Address(clean.host, clean.rawPath.trimEnd('/'))
        }
    }
}

class Allowlist(
    private val addresses: List<Address>,
) {
    fun refusalOf(url: String): UrlProblem? =
        when (val inspection = admit(url)) {
            is Inspection.Clean -> null
            is Inspection.Refused -> inspection.problem
        }

    internal fun admit(url: String): Inspection {
        val inspection = inspect(url, isDeclared = false)
        if (inspection !is Inspection.Clean) return inspection
        return if (addresses.any { it.admits(inspection.host, inspection.rawPath) }) {
            inspection
        } else {
            Inspection.Refused(UrlProblem.NOT_DECLARED)
        }
    }

    companion object {
        /** ADR-0008: the only place Darek connects to for himself, with no grant. */
        val DAREK = Allowlist(listOf(requireNotNull(Address.parse("https://maksforge.com/darek-fuchy/"))))
    }
}

internal sealed interface Inspection {
    data class Clean(
        val host: String,
        val rawPath: String,
        val rawQuery: String?,
    ) : Inspection

    data class Refused(
        val problem: UrlProblem,
    ) : Inspection
}

private fun inspect(
    text: String,
    isDeclared: Boolean,
): Inspection {
    val uri =
        try {
            URI(text)
        } catch (_: URISyntaxException) {
            return Inspection.Refused(UrlProblem.MALFORMED)
        }
    val problem =
        when {
            !uri.isAbsolute || uri.isOpaque -> UrlProblem.MALFORMED
            !uri.scheme.equals("https", ignoreCase = true) -> UrlProblem.NOT_HTTPS
            uri.rawUserInfo != null -> UrlProblem.USERINFO
            uri.port != -1 -> UrlProblem.EXPLICIT_PORT
            uri.host.isNullOrEmpty() -> UrlProblem.MALFORMED
            uri.rawFragment != null -> UrlProblem.FRAGMENT
            isDeclared && uri.rawQuery != null -> UrlProblem.QUERY
            '\\' in uri.path || uri.path.split('/').any { it == "." || it == ".." } -> UrlProblem.PATH_TRAVERSAL
            else -> null
        }
    if (problem != null) return Inspection.Refused(problem)
    return Inspection.Clean(uri.host.lowercase(Locale.ROOT), uri.rawPath.orEmpty(), uri.rawQuery)
}
