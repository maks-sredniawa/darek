package app.darek.ui.fuchy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import app.darek.R
import app.darek.fuchy.Fucha
import app.darek.ui.common.startActivitySafely
import app.darek.ui.support.SupportConfig
import app.darek.ui.support.versionLine

/** The fucha's ID and version in the subject's tag, so a report sorts by fucha (phase-F.md section 7). */
fun reportSubject(fucha: Fucha): String = "${SupportConfig.MAIL_TAG} #fucha ${fucha.id} v${fucha.version}"

/** *Zgłoś fuchę*: the support hand-off's mail, with its own tag; `SupportHandoff.kt` is synced, so it is not extended. */
fun Context.reportFucha(fucha: Fucha): Boolean {
    val body =
        buildString {
            appendLine(getString(R.string.report_body, fucha.name))
            appendLine()
            appendLine()
            appendLine("---")
            appendLine(versionLine())
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}")
        }
    val uri =
        (
            "mailto:${SupportConfig.SUPPORT_ADDRESS}" +
                "?subject=" + Uri.encode(reportSubject(fucha)) +
                "&body=" + Uri.encode(body)
        ).toUri()
    return startActivitySafely(Intent(Intent.ACTION_SENDTO, uri))
}
