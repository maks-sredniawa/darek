package app.darek.fuchy

import android.content.Context

/** Darek's own fucha, approved by being inside the APK rather than signed (phase-F.md section 2). */
object OwnFucha {
    const val ID = "darek"
    const val ASSET = "fuchy/darek.darek"

    fun load(context: Context): Fucha = from(context.assets.open(ASSET).use { it.readBytes() })

    // Its words were read against the model when they shipped; the vocabulary check is for fuchy from outside the APK.
    fun from(file: ByteArray): Fucha =
        when (val checked = checked(file, knows = { true }, bundled = true)) {
            is Checked.Accepted -> checked.fucha
            is Checked.Refused -> error("Darek's own fucha is refused: ${checked.refusals}")
        }
}
