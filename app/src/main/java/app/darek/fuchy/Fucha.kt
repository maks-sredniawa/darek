package app.darek.fuchy

import app.darek.listen.Command

/** ADR-0015's fixed lists. A `.darek` file writes each value as its name in lower case. */
enum class Action(
    val needs: Permission?,
) {
    SPEAK(null),
    CALL(Permission.CALL),
}

enum class Permission { CALL, INTERNET }

enum class SentValue { CHOSEN_CITY }

internal inline fun <reified T : Enum<T>> named(wire: String): T? {
    val values = enumValues<T>()
    return values.firstOrNull { it.name.lowercase() == wire }
}

/** A fucha that passed every install check (ADR-0015); [bundled] is Darek's own, approved by being inside the APK. */
data class Fucha(
    val id: String,
    val version: Int,
    val name: String,
    val description: String,
    val author: String,
    val profanity: Boolean,
    val permissions: List<Permission>,
    val addresses: List<String>,
    val sends: List<SentValue>,
    val commands: List<Command>,
    val bundled: Boolean,
    val signature: String?,
) {
    /** In the grammar while this fucha is on, even when its own Command cannot act (one that calls, with no Number). */
    val decoys: List<String> get() = commands.flatMap { it.decoys }.distinct()
}
