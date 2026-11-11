package app.darek.listen

/**
 * Picks the partial that hails [name] once it has held unchanged for [STEADY_MS] of audio: a passing
 * flicker of the hail, as „hej marek" settles, is not answered, and each utterance is answered once.
 */
class NameHeard(
    private val name: String,
) {
    private var heard = false

    fun isFirst(result: Heard): Boolean =
        when (result) {
            is Heard.Final -> {
                heard = false
                false
            }
            is Heard.Partial -> {
                val first = !heard && result.steadyMs >= STEADY_MS && result.tokens.hails(name)
                heard = heard || first
                first
            }
        }

    companion object {
        const val STEADY_MS = 400L
    }
}
