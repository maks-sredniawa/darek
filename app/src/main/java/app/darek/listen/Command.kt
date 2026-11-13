package app.darek.listen

import app.darek.fuchy.Action
import app.darek.fuchy.Fucha
import java.util.Locale

/**
 * A Command from a fucha (ADR-0015), its phrase written without the name: the chosen Persona's goes
 * in front (ADR-0016). Words are lower-case, as the recogniser emits them. [bundled] is Darek's own.
 */
data class Command(
    val id: String,
    val fucha: String,
    val phrase: String,
    val action: Action,
    val reply: Line,
    val decoys: List<String> = emptyList(),
    val bundled: Boolean = false,
) {
    val calls: Boolean get() = action == Action.CALL

    /** Darek's own keep the names they logged as an enum (`reaction=GATE`), which docs and readings grep for. */
    val label: String get() = if (bundled) id.uppercase(Locale.ROOT).replace('-', '_') else "$fucha/$id"

    private val phraseWords = phrase.split(' ')

    fun words(persona: Persona): List<String> = listOf(persona.word) + phraseWords

    /** As the user says it, *„Darek, otwórz bramę"*: Polish in every locale (ADR-0012). */
    fun spoken(persona: Persona): String = "${persona.word.replaceFirstChar { it.uppercase() }}, $phrase"
}

fun enabledCommands(
    on: Collection<Fucha>,
    number: String?,
): Set<Command> = on.flatMap { it.commands }.filter { !it.calls || number != null }.toSet()

/** Every Decoy of the fuchy that are on, whether or not their Commands are enabled. */
fun commandDecoys(on: Collection<Fucha>): List<String> = on.flatMap { it.decoys }.distinct()
