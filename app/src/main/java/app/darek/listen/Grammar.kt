package app.darek.listen

import kotlinx.serialization.json.Json

/**
 * The recogniser's grammar (ADR-0009): each enabled Command as a phrase and word by word, *„hej"*
 * and the hail, the Decoys alone, the name Decoys in front of each Command's phrase, the cancel
 * words while a Command that calls is enabled, and `[unk]`. Command Decoys come from the fuchy that
 * are on (ADR-0015); the name Decoys belong to the Persona and stay here (ADR-0016).
 *
 * kotlinx.serialization writes `coś` as UTF-8; Vosk takes a `ś` escape for an unknown word and
 * drops it, and the Command could never fire.
 */
object Grammar {
    /** The same under either Persona; the other Persona's name joins them (ADR-0016). */
    val NAME_DECOYS =
        listOf("marek", "jarek", "janek", "dawid", "maria", "marii", "darka", "darku", "darii", "darię", "dario")
    val CANCEL_WORDS = listOf("stop", "anuluj")
    const val UNKNOWN = "[unk]"

    fun decoys(
        persona: Persona,
        commandDecoys: List<String>,
    ): List<String> = NAME_DECOYS + persona.other.word + commandDecoys

    /** Each name Decoy in front of each enabled Command's phrase (ADR-0009). */
    fun decoyPhrases(
        enabled: Collection<Command>,
        persona: Persona,
    ): List<String> = (NAME_DECOYS + persona.other.word).flatMap { name -> enabled.map { "$name ${it.phrase}" } }

    fun phrases(
        enabled: Collection<Command>,
        commandDecoys: List<String>,
        persona: Persona,
    ): List<String> =
        buildList {
            enabled.forEach { command ->
                val words = command.words(persona)
                add(words.joinToString(" "))
                addAll(words)
            }
            add("$HEJ ${persona.word}")
            add(HEJ)
            addAll(decoys(persona, commandDecoys))
            addAll(decoyPhrases(enabled, persona))
            if (enabled.any { it.calls }) addAll(CANCEL_WORDS)
            add(UNKNOWN)
        }.distinct()

    fun json(
        enabled: Collection<Command>,
        commandDecoys: List<String>,
        persona: Persona,
        extra: List<String> = emptyList(),
    ): String = Json.encodeToString((phrases(enabled, commandDecoys, persona) + extra).distinct())
}
