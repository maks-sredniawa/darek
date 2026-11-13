package app.darek.fuchy

import app.darek.listen.Command
import app.darek.listen.Persona
import java.util.Locale

/** ADR-0015's *Phrases that clash*, judged between a fucha about to be installed and the Commands already on. */
sealed interface Clash {
    /** Never both on: switching one on switches the other off. */
    val neverBothOn: Boolean get() = false

    /** Identical, or one holding the other's words adjacent and in order, which ADR-0009's match rule would fire. */
    data class Holds(
        val candidate: Command,
        val enabled: Command,
    ) : Clash {
        override val neverBothOn get() = true
    }

    /** Within two letters over the whole phrase: installs off, behind a warning. */
    data class SoundsLike(
        val candidate: Command,
        val enabled: Command,
    ) : Clash

    /** A Decoy or Command word within two letters of a Persona's name, under either, since the user can switch. */
    data class NearName(
        val word: String,
        val persona: Persona,
    ) : Clash

    /** The grammar holds every Command word as a token of its own, so a near one would take that Command's utterances. */
    data class NearWord(
        val word: String,
        val enabled: Command,
        val near: String,
    ) : Clash
}

private const val NEAR = 2

fun clashes(
    candidate: Fucha,
    enabled: Collection<Command>,
): List<Clash> {
    val others = enabled.filter { it.fucha != candidate.id }
    val phrases =
        candidate.commands.flatMap { mine ->
            others.mapNotNull { other ->
                val a = wordsOf(mine.phrase)
                val b = wordsOf(other.phrase)
                when {
                    a.holds(b) || b.holds(a) -> Clash.Holds(mine, other)
                    editDistance(a.joinToString(" "), b.joinToString(" ")) <= NEAR -> Clash.SoundsLike(mine, other)
                    else -> null
                }
            }
        }
    val words = (candidate.commands.flatMap { wordsOf(it.phrase) } + candidate.decoys.flatMap(::wordsOf)).distinct()
    val names =
        words.flatMap { word ->
            Persona.entries.filter { near(word, it.word) }.map { Clash.NearName(word, it) }
        }
    val commandWords =
        words.flatMap { word ->
            others.flatMap { other ->
                wordsOf(other.phrase).filter { near(word, it) }.map { Clash.NearWord(word, other, it) }
            }
        }
    return (phrases + names + commandWords).distinct()
}

/** Lower case, punctuation dropped, whitespace collapsed: how a phrase is compared, and how it enters the grammar. */
fun normalised(phrase: String): String = wordsOf(phrase).joinToString(" ")

internal fun wordsOf(phrase: String): List<String> =
    phrase
        .lowercase(Locale.ROOT)
        .replace(PUNCTUATION, "")
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }

private val PUNCTUATION = Regex("[^\\p{L}\\p{N}\\s]")
private val WHITESPACE = Regex("\\s+")

/** Equal is the same token, and changes nothing. */
private fun near(
    a: String,
    b: String,
): Boolean = editDistance(a, b) in 1..NEAR

private fun List<String>.holds(other: List<String>): Boolean =
    other.isNotEmpty() && (0..size - other.size).any { start -> other.indices.all { this[start + it] == other[it] } }

/** Levenshtein: insertions, deletions and substitutions, each one letter. */
fun editDistance(
    a: String,
    b: String,
): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, substitution)
        }
        previous = current
    }
    return previous[b.length]
}
