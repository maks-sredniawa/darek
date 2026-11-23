package app.darek.fuchy

import android.util.Log
import app.darek.data.AppPreferences
import app.darek.listen.Line
import app.darek.net.Allowlist
import app.darek.net.HttpClient
import app.darek.net.HttpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

sealed interface PageFetch {
    data class Listed(
        val listings: List<Listing>,
    ) : PageFetch

    data object NewerFormat : PageFetch

    data object NoInternet : PageFetch

    data object Failed : PageFetch
}

sealed interface Download {
    data class Got(
        val bytes: ByteArray,
    ) : Download

    data object NoInternet : Download

    data object Failed : Download
}

/** What one round of updates did: [waiting] widen what their fucha may do and wait for the user (phase-F.md section 7). */
data class Updated(
    val updated: List<Fucha> = emptyList(),
    val waiting: List<Listing> = emptyList(),
    val failed: List<Listing> = emptyList(),
)

sealed interface UpdateCheck {
    /** No approved fucha but Darek's own is installed, so nothing was read. */
    data object NothingToUpdate : UpdateCheck

    data class Done(
        val updated: Updated,
    ) : UpdateCheck

    data class NotRead(
        val fetch: PageFetch,
    ) : UpdateCheck
}

/**
 * The fuchy page at `maksforge.com` (phase-F.md section 7): the only address Darek reaches for himself
 * (ADR-0008). [vocabulary] loads the recogniser's model for the install check and throws
 * [IOException] when it cannot.
 */
class FuchyPage(
    private val fuchy: Fuchy,
    private val preferences: AppPreferences,
    private val vocabulary: suspend () -> (String) -> Boolean,
    private val client: HttpClient = HttpClient(Allowlist.DAREK),
    private val key: PublicKey = FuchyKey.maksForge,
    private val now: () -> Instant = Instant::now,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) {
    private val updating = Mutex()

    suspend fun read(): PageFetch =
        when (val result = client.get(PAGE_URL)) {
            is HttpResult.Response ->
                when (val page = readPage(result.body)) {
                    is PageRead.Listed -> PageFetch.Listed(page.listings).also { preferences.markPageChecked(now()) }
                    PageRead.NewerFormat -> PageFetch.NewerFormat
                    PageRead.Unreadable -> PageFetch.Failed
                }
            HttpResult.TimedOut, is HttpResult.Network -> PageFetch.NoInternet
            else -> PageFetch.Failed.also { log("page failed $result") }
        }

    suspend fun download(listing: Listing): Download =
        when (val result = client.get(listing.url)) {
            is HttpResult.Response -> Download.Got(result.body)
            HttpResult.TimedOut, is HttpResult.Network -> Download.NoInternet
            else -> Download.Failed.also { log("${listing.id} failed $result") }
        }

    /** Every approved fucha the page has a newer version of, installed unless it must wait for the user. */
    suspend fun update(listings: List<Listing>): Updated =
        updating.withLock {
            val due =
                pageRows(listings, fuchy.installed.first(), showProfanity = true)
                    .filter { it.offer is Offer.Update }
            due.fold(Updated()) { done, row -> done + updateOne(row.listing) }
        }

    /** Reads the page and applies what need not wait, whether or not updates are automatic: the user asked. */
    suspend fun checkForUpdates(): UpdateCheck {
        if (!hasUpdatable()) return UpdateCheck.NothingToUpdate
        return when (val fetch = read()) {
            is PageFetch.Listed -> UpdateCheck.Done(update(fetch.listings))
            else -> UpdateCheck.NotRead(fetch)
        }
    }

    /** At process start, at most once a day, only with automatic updates on and something to update. */
    suspend fun dailyCheck() {
        if (!preferences.autoUpdate.first() || !hasUpdatable()) return
        val last = preferences.pageCheckedAt.first()
        if (last != null && Duration.between(last, now()) < DAY && !last.isAfter(now())) return
        log("daily check ${checkForUpdates().outcome()}")
    }

    /** What *„Darek, zaktualizuj fuchy"* says. */
    suspend fun reply(): Line {
        val check = withTimeoutOrNull(VOICE_LIMIT) { checkForUpdates() }
        log("voice update ${check?.outcome() ?: "timed_out"}")
        return check?.let(::lineFor) ?: UPDATE_FAILED
    }

    private suspend fun hasUpdatable(): Boolean = fuchy.installed.first().any { !it.fucha.bundled && it.fucha.approved }

    private suspend fun updateOne(listing: Listing): Updated {
        val bytes =
            when (val download = download(listing)) {
                is Download.Got -> download.bytes
                else -> return Updated(failed = listOf(listing))
            }
        val knows =
            try {
                vocabulary()
            } catch (e: IOException) {
                return Updated(failed = listOf(listing)).also { Log.e(TAG, "model failed to load", e) }
            }
        val checked = withContext(Dispatchers.Default) { checked(bytes, knows, key = key) }
        val new = (checked as? Checked.Accepted)?.fucha?.takeIf { it.id == listing.id }
        val installed = fuchy.installed.first()
        val old = installed.firstOrNull { it.fucha.id == listing.id }?.fucha
        return when {
            new == null || old == null || new.version <= old.version ->
                Updated(failed = listOf(listing)).also { log("${listing.id} not newer, or refused: $checked") }
            updateWaits(old, new) -> Updated(waiting = listOf(listing))
            else ->
                try {
                    // An update cut short between the file and its state would come back off after a restart.
                    withContext(NonCancellable) { fuchy.install(bytes, planInstall(new, installed)) }
                    Updated(updated = listOf(new))
                } catch (e: IOException) {
                    Updated(failed = listOf(listing)).also { Log.e(TAG, "could not save ${listing.id}", e) }
                }
        }
    }

    private operator fun Updated.plus(other: Updated) =
        Updated(updated + other.updated, waiting + other.waiting, failed + other.failed)

    private fun UpdateCheck.outcome(): String =
        when (this) {
            UpdateCheck.NothingToUpdate -> "nothing_installed"
            is UpdateCheck.NotRead -> "not_read $fetch"
            is UpdateCheck.Done ->
                "updated=${updated.updated.map { it.id }} waiting=${updated.waiting.map { it.id }} " +
                    "failed=${updated.failed.map { it.id }}"
        }

    private companion object {
        const val TAG = "DarekFuchyPage"
        val DAY: Duration = Duration.ofDays(1)
        val VOICE_LIMIT = 15.seconds
    }
}

val NOTHING_TO_UPDATE = Line("Poza moimi nie mam żadnej fuchy do zaktualizowania.")

val UPDATE_NO_INTERNET = Line("Nie mam internetu, nie sprawdzę fuch.")

val UPDATE_FAILED = Line("Nie udało się sprawdzić fuch.")

val ALL_UP_TO_DATE = Line("Wszystkie fuchy są aktualne.")

/** Names rather than counts, so no line needs Polish plurals. */
fun lineFor(check: UpdateCheck): Line =
    when (check) {
        UpdateCheck.NothingToUpdate -> NOTHING_TO_UPDATE
        is UpdateCheck.NotRead -> if (check.fetch == PageFetch.NoInternet) UPDATE_NO_INTERNET else UPDATE_FAILED
        is UpdateCheck.Done -> {
            val done = check.updated
            val names = { list: List<String> -> list.joinToString(", ") }
            val parts =
                listOfNotNull(
                    done.updated.takeIf { it.isNotEmpty() }?.map { it.name }?.let {
                        Line("Zaktualizowałem: ${names(it)}.", "Zaktualizowałam: ${names(it)}.")
                    },
                    done.waiting.takeIf { it.isNotEmpty() }?.let {
                        Line("Na twoją zgodę na stronie z fuchami czeka: ${names(it.map(Listing::name))}.")
                    },
                    done.failed.takeIf { it.isNotEmpty() }?.let {
                        Line("Nie udało się zaktualizować: ${names(it.map(Listing::name))}.")
                    },
                )
            if (parts.isEmpty()) ALL_UP_TO_DATE else parts.reduce { a, b -> a + b }
        }
    }
