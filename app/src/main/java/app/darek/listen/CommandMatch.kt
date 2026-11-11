package app.darek.listen

/**
 * ADR-0009's rule: the Command whose words appear adjacent and in order in one final result's raw
 * tokens, `[unk]` included, with anything before or after ignored. Partial results never come here.
 */
fun commandIn(
    tokens: List<String>,
    enabled: Collection<Command>,
    persona: Persona,
): Command? =
    tokens.indices.firstNotNullOfOrNull { start ->
        enabled.firstOrNull { command -> tokens.hasAt(start, command.words(persona)) }
    }

/** Said directly before a name, it asks for an answer that is not a Command (phase-2.md, section 4). */
const val HEJ = "hej"

fun List<String>.hails(name: String): Boolean = indices.any { this[it] == HEJ && getOrNull(it + 1) == name }

/**
 * What one final result makes the Persona do: fire a Command; when hailed with its own name and
 * nothing it knows, say it does not understand, or, while no Command the user sets up is enabled,
 * say it has no fucha; failing that, correct a user who hailed the other Persona (ADR-0016); or stay
 * silent, so talk that names either without *„hej"* never gets an answer. The built-in Command that
 * only replies does not count as set up (the owner's call, 2026-11-01).
 */
fun reactionTo(
    tokens: List<String>,
    enabled: Collection<Command>,
    persona: Persona,
): Reaction? {
    commandIn(tokens, enabled, persona)?.let { return Reaction.Fires(it) }
    return when {
        tokens.hails(persona.word) -> if (enabled.any { it.calls }) Reaction.NotUnderstood else Reaction.NotConfigured
        tokens.hails(persona.other.word) -> Reaction.Corrected
        else -> null
    }
}

sealed interface Reaction {
    data class Fires(
        val command: Command,
    ) : Reaction

    data object NotUnderstood : Reaction

    data object NotConfigured : Reaction

    data object Corrected : Reaction
}

val NOT_CONFIGURED = Line("Stary, nie mam żadnej fuchy.")

val NAME_HEARD = Line("No?")

val CALL_CANCELLED = Line("Anulowane.")

val CALL_FAILED = Line("Nie udało się zadzwonić.")

/** Picked at random, never the same twice in a row. */
val NOT_UNDERSTOOD =
    listOf(
        Line("Stary, nie rozumiem cię."),
        Line("Co? Nie kumam."),
        Line("Sorry, nie załapałem.", "Sorry, nie załapałam."),
        Line("Powtórz, bo nie zrozumiałem.", "Powtórz, bo nie zrozumiałam."),
        Line("Mordo, nie wiem, o co ci chodzi."),
        Line("Hę? Możesz jeszcze raz?"),
        Line("Nie ogarniam, powiedz to inaczej."),
        Line("Słyszę cię, ale nie wiem, czego chcesz."),
    )

fun tokensOf(text: String): List<String> = text.split(' ', '\t', '\n').filter { it.isNotEmpty() }

private fun List<String>.hasAt(
    start: Int,
    words: List<String>,
): Boolean = start + words.size <= size && words.indices.all { this[start + it] == words[it] }
