package app.darek.listen

import kotlinx.serialization.json.Json

/**
 * The recogniser's grammar (ADR-0009): each enabled Command as a phrase and word by word, *„hej"*
 * and the hail, the Decoys alone and in front of each Command's phrase, the cancel words while a Command that calls is enabled, and `[unk]`.
 *
 * kotlinx.serialization writes `coś` as UTF-8; Vosk takes a `ś` escape for an unknown word and
 * drops it, and the Command could never fire.
 */
object Grammar {
    /** The same under either Persona; the other Persona's name joins them (ADR-0016). */
    val NAME_DECOYS =
        listOf("marek", "jarek", "janek", "dawid", "maria", "marii", "darka", "darku", "darii", "darię", "dario")
    val COMMAND_WORD_DECOYS = listOf("otwórz", "zamknij", "brama")
    val CANCEL_WORDS = listOf("stop", "anuluj")
    const val UNKNOWN = "[unk]"

    fun decoys(persona: Persona): List<String> = NAME_DECOYS + persona.other.word + COMMAND_WORD_DECOYS

    /** Each name Decoy in front of each enabled Command's phrase (ADR-0009). */
    fun decoyPhrases(
        enabled: Set<Command>,
        persona: Persona,
    ): List<String> = (NAME_DECOYS + persona.other.word).flatMap { name -> enabled.map { "$name ${it.phrase}" } }

    fun phrases(
        enabled: Set<Command>,
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
            addAll(decoys(persona))
            addAll(decoyPhrases(enabled, persona))
            if (enabled.any { it.calls }) addAll(CANCEL_WORDS)
            add(UNKNOWN)
        }.distinct()

    fun json(
        enabled: Set<Command>,
        persona: Persona,
        extra: List<String> = emptyList(),
    ): String = Json.encodeToString((phrases(enabled, persona) + extra).distinct())
}
