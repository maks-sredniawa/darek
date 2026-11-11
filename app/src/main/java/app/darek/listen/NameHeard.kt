package app.darek.listen

/** Picks the first partial of an utterance that hails [name]: partials repeat and flicker, and each utterance is answered once. */
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
                val first = !heard && result.tokens.hails(name)
                heard = heard || first
                first
            }
        }
}
