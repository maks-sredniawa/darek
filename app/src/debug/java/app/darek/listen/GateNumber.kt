package app.darek.listen

import android.content.Context
import androidx.core.content.edit

object GateNumber {
    private const val FILE = "debug_gate"
    private const val KEY = "number"

    fun get(context: Context): String? =
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.takeIf { it.isNotBlank() }

    fun set(
        context: Context,
        number: String,
    ) {
        context
            .getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit { putString(KEY, number.trim()) }
    }
}
