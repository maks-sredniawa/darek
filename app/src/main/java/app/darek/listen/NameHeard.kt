package app.darek.listen

/** Picks the first partial of an utterance that holds [name]: partials repeat and flicker, and each utterance is answered once. */
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
                val first = !heard && name in result.tokens
                heard = heard || first
                first
            }
        }
}
