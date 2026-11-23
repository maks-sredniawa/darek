package app.darek.listen

/**
 * Who answers (ADR-0016): the name every Command starts with, the voice, and the gender of every
 * line. Stored by name, so adding one never rewrites a stored choice.
 */
enum class Persona(
    val word: String,
    val voice: String,
    val correction: String,
    val voiceScales: FloatArray? = null,
) {
    DAREK("darek", "voice/darek", "Daria? Tu Darek, stary."),

    // gosia lisps at her config's 0.667/1/0.8; the owner judged 0.4/1/0.5 best by ear, 2026-11-01.
    DARIA("daria", "voice/daria", "Nie jestem Darkiem, mówisz z Darią.", floatArrayOf(0.4f, 1f, 0.5f)),
    ;

    val other: Persona get() = if (this == DAREK) DARIA else DAREK
}

/** Speech in both genders; a line with no gendered word is written once. Speech, not UI (ADR-0012). */
data class Line(
    private val darek: String,
    private val daria: String = darek,
) {
    fun by(persona: Persona): String = if (persona == Persona.DAREK) darek else daria

    operator fun plus(next: Line): Line = Line("$darek ${next.darek}", "$daria ${next.daria}")
}
