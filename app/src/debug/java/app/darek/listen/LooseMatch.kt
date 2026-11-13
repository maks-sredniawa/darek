package app.darek.listen

import android.content.Context
import androidx.core.content.edit
import app.darek.fuchy.editDistance

/**
 * Debug-only: lets a reply-only Command fire when each of its words is only *alike* what was heard,
 * letter by letter. The name, its first word, stays exact, or a Marek would fire it; Commands that
 * call never come here (ADR-0009).
 */
object LooseMatch {
    private const val FILE = "debug_probe"
    private const val KEY = "likeness"

    fun likeness(context: Context): Float = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat(KEY, 0.7f)

    fun setLikeness(
        context: Context,
        likeness: Float,
    ) {
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putFloat(KEY, likeness) }
    }

    fun matches(
        tokens: List<String>,
        words: List<String>,
        likeness: Float,
    ): Boolean =
        tokens.windowed(words.size).any { window ->
            window.zip(words).withIndex().all { (i, pair) ->
                val (heard, word) = pair
                heard == word || (i > 0 && alike(heard, word) >= likeness)
            }
        }

    fun alike(
        a: String,
        b: String,
    ): Float {
        val longest = maxOf(a.length, b.length)
        return if (longest == 0) 1f else 1f - editDistance(a, b).toFloat() / longest
    }
}
