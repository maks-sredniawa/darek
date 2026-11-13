package app.darek

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every route in the app, as a key.
 *
 * Kotlin note: `data object` is a singleton with a sensible `toString`/`equals` — the right shape
 * for a route with no arguments. A route that carries arguments is a `data class` instead, and
 * `@Serializable` is what lets Nav3 save the whole back stack across process death.
 */
@Serializable data object Home : NavKey

@Serializable data object Settings : NavKey

/**
 * Help and feedback, and the licences below it — both pushed from Settings' *About* section.
 * Somewhere you go once, from the place you already go to look things up.
 */
@Serializable data object Support : NavKey

@Serializable data object Licences : NavKey

@Serializable
data class LicenceText(
    val spdxId: String,
    val title: String,
) : NavKey
