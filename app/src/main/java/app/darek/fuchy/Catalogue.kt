package app.darek.fuchy

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

/** `https://maksforge.com/darek-fuchy/fuchy.json`, as `darek-fuchy`'s signing workflow writes it (phase-F.md section 7). */
const val CATALOGUE_URL = "https://maksforge.com/darek-fuchy/fuchy.json"

/** One approved fucha on the page; [actions] stay names, so an unknown one greys the row instead of failing the page. */
@Serializable
data class CatalogueEntry(
    val id: String,
    val version: Int,
    val name: String,
    val description: String,
    val author: String,
    val profanity: Boolean = false,
    val actions: List<String> = emptyList(),
    val url: String,
) {
    /** Shown as *„Wymaga nowszej wersji Darka"*: an action this Darek does not know, or one kept for his own fucha. */
    val needsNewerDarek: Boolean get() = actions.any { named<Action>(it)?.ownOnly ?: true }
}

sealed interface Catalogue {
    data class Read(
        val entries: List<CatalogueEntry>,
    ) : Catalogue

    data class NewerFormat(
        val format: Int,
    ) : Catalogue

    data object Unreadable : Catalogue
}

private val json = Json { ignoreUnknownKeys = true }

@Serializable
private class CatalogueFile(
    val fuchy: List<CatalogueEntry>,
)

/** Like a fucha's file, the format is read first, so a newer page says it needs a newer Darek rather than that it is broken. */
fun parseCatalogue(bytes: ByteArray): Catalogue =
    runCatching {
        val tree = json.parseToJsonElement(bytes.decodeToString()) as? JsonObject ?: return Catalogue.Unreadable
        val format = (tree["format"] as? JsonPrimitive)?.intOrNull ?: return Catalogue.Unreadable
        when {
            format > FORMAT -> Catalogue.NewerFormat(format)
            format < FORMAT -> Catalogue.Unreadable
            else -> Catalogue.Read(json.decodeFromJsonElement<CatalogueFile>(tree).fuchy)
        }
    }.getOrElse { Catalogue.Unreadable }

/** What a newer version from the page may do on its own (ADR-0015, *Updates*; phase-F.md section 7). */
sealed interface UpdateDecision {
    /** Nothing it may do widens and nothing clashes, so it installs without asking when updates are automatic. */
    data object Automatic : UpdateDecision

    /** It goes through the install screen, for the user to see what changed. */
    data class Waits(
        val because: List<WaitsBecause>,
    ) : UpdateDecision
}

enum class WaitsBecause { NEW_PERMISSION, NEW_ADDRESS, NEW_SENT_VALUE, CLASH, UNAPPROVED }

fun updateDecision(plan: InstallPlan): UpdateDecision {
    val old = checkNotNull(plan.replaces) { "an update replaces an installed fucha" }.fucha
    val new = plan.candidate
    val because =
        buildList {
            if (!(new.permissions - old.permissions.toSet()).isEmpty()) add(WaitsBecause.NEW_PERMISSION)
            if (!(new.addresses - old.addresses.toSet()).isEmpty()) add(WaitsBecause.NEW_ADDRESS)
            if (!(new.sends - old.sends.toSet()).isEmpty()) add(WaitsBecause.NEW_SENT_VALUE)
            if (plan.clashes.isNotEmpty()) add(WaitsBecause.CLASH)
            if (!new.approved || plan.fresh) add(WaitsBecause.UNAPPROVED)
        }
    return if (because.isEmpty()) UpdateDecision.Automatic else UpdateDecision.Waits(because)
}
