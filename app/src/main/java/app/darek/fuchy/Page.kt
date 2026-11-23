package app.darek.fuchy

import app.darek.net.Allowlist
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull

const val PAGE_URL = "https://maksforge.com/darek-fuchy/fuchy.json"

/** One approved fucha as `fuchy.json` lists it (phase-F.md section 7); [actions] say whether this Darek can run it. */
@Serializable
data class Listing(
    val id: String,
    val version: Int,
    val name: String,
    val description: String,
    val author: String,
    val profanity: Boolean,
    val actions: List<String>,
    val url: String,
)

@Serializable
private class Catalogue(
    val format: Int,
    val fuchy: List<Listing>,
)

sealed interface PageRead {
    data class Listed(
        val listings: List<Listing>,
    ) : PageRead

    /** A `fuchy.json` of a later format than this Darek reads. */
    data object NewerFormat : PageRead

    data object Unreadable : PageRead
}

private val pageJson = Json { ignoreUnknownKeys = true }

/** A listing whose file is anywhere but the fuchy page is dropped, so the page can only lead to `maksforge.com`. */
fun readPage(body: ByteArray): PageRead =
    try {
        val tree = pageJson.parseToJsonElement(body.decodeToString(throwOnInvalidSequence = true)) as? JsonObject
        val format = (tree?.get("format") as? JsonPrimitive)?.intOrNull
        when {
            tree == null || format == null || format < FORMAT -> PageRead.Unreadable
            format > FORMAT -> PageRead.NewerFormat
            else ->
                PageRead.Listed(
                    pageJson
                        .decodeFromJsonElement<Catalogue>(tree)
                        .fuchy
                        .filter { Allowlist.DAREK.refusalOf(it.url) == null }
                        .distinctBy { it.id },
                )
        }
    } catch (e: IllegalArgumentException) {
        PageRead.Unreadable
    } catch (e: CharacterCodingException) {
        PageRead.Unreadable
    }

/** What a row on the page offers. */
sealed interface Offer {
    data object Install : Offer

    data class Update(
        val from: Int,
    ) : Offer

    data object UpToDate : Offer

    /** An action this Darek does not know, or one only his own fucha may use. */
    data object NeedsNewerDarek : Offer
}

data class PageRow(
    val listing: Listing,
    val installed: Installed?,
    val offer: Offer,
)

/**
 * Installed fuchy first, then the rest, each in the page's order. A profanity fucha is hidden until the
 * user shows them, unless it is already installed; Darek's own is never on the page.
 */
fun pageRows(
    listings: List<Listing>,
    installed: List<Installed>,
    showProfanity: Boolean,
): List<PageRow> {
    val byId = installed.associateBy { it.fucha.id }
    val rows =
        listings
            .filter { it.id != OwnFucha.ID }
            .map { PageRow(it, byId[it.id], offerFor(it, byId[it.id])) }
            .filter { showProfanity || !it.listing.profanity || it.installed != null }
    return rows.filter { it.installed != null } + rows.filter { it.installed == null }
}

/** The page's own copy of a fucha replaces only an approved one: an unapproved file is never updated from it. */
private fun offerFor(
    listing: Listing,
    installed: Installed?,
): Offer =
    when {
        listing.actions.any { name -> named<Action>(name).let { it == null || it.ownOnly } } -> Offer.NeedsNewerDarek
        installed == null -> Offer.Install
        installed.fucha.approved && listing.version > installed.fucha.version -> Offer.Update(installed.fucha.version)
        installed.fucha.approved -> Offer.UpToDate
        else -> Offer.Install
    }

/**
 * An update that widens what a fucha may do waits for the user on the page: a new permission, address
 * or value sent (phase-F.md section 7). So does any file that is not approved over an approved one.
 */
fun updateWaits(
    old: Fucha,
    new: Fucha,
): Boolean =
    !new.approved ||
        !old.permissions.containsAll(new.permissions) ||
        !old.addresses.containsAll(new.addresses) ||
        !old.sends.containsAll(new.sends)
