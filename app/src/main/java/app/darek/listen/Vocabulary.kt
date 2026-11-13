package app.darek.listen

import com.sun.jna.Native
import com.sun.jna.Pointer
import org.vosk.Model

/**
 * Whether the recogniser's model knows a word: Vosk drops a grammar word it does not know, silently,
 * so a fucha's Command holding one could never fire (ADR-0015). Vosk's Java binding leaves
 * `vosk_model_find_word` out, so it is bound here, the same way `org.vosk.LibVosk` binds the rest.
 */
object Vocabulary {
    init {
        Native.register(Vocabulary::class.java, "vosk")
    }

    fun knows(
        model: Model,
        word: String,
    ): Boolean = vosk_model_find_word(model.pointer, word) >= 0

    @Suppress("ktlint:standard:function-naming")
    @JvmStatic
    private external fun vosk_model_find_word(
        model: Pointer,
        word: String,
    ): Int
}
