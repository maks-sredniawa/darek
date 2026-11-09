package app.darek.listen

/**
 * The catalogue (ADR-0009), each phrase written without the name: the chosen Persona's goes in front
 * (ADR-0016). Words are lower-case, as the recogniser emits them.
 */
enum class Command(
    phrase: String,
    val reply: Line,
    val calls: Boolean,
) {
    SAY_SOMETHING("powiedz coś", Line("Co tam, mordo?"), calls = false),
    GATE("otwórz bramę", Line("Otwieram bramę."), calls = true),
    ;

    private val phraseWords = phrase.split(' ')

    fun words(persona: Persona): List<String> = listOf(persona.word) + phraseWords
}

fun enabledCommands(number: String?): Set<Command> = Command.entries.filter { !it.calls || number != null }.toSet()
