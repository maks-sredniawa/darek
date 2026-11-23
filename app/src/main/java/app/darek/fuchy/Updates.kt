package app.darek.fuchy

import android.util.Log
import app.darek.data.AppPreferences
import app.darek.listen.Line
import app.darek.net.Allowlist
import app.darek.net.HttpClient
import app.darek.net.HttpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.PublicKey
import java.time.Duration
import java.time.Instant

sealed interface Fetched<out T> {
    data class Got<T>(
        val value: T,
    ) : Fetched<T>

    data object NoInternet : Fetched<Nothing>

    data object Failed : Fetched<Nothing>
}

/** What one check for updates did; [waiting] go through the install screen from the page. */
sealed interface UpdateRun {
    /** No approved fucha but Darek's own is installed, so nothing was asked of the network. */
    data object NothingToUpdate : UpdateRun

    data object NoInternet : UpdateRun

    data object Failed : UpdateRun

    data class Done(
        val updated: List<Fucha>,
        val waiting: List<Fucha>,
    ) : UpdateRun
}

/**
 * The page's reads and the updates (phase-F.md section 7). Every request goes to maksforge.com through
 * [Allowlist.DAREK], which needs no grant (ADR-0008); an update is checked exactly as an install is.
 */
class FuchyUpdates(
    private val fuchy: Fuchy,
    /** The recogniser model's word check, loaded only when an update is about to be checked. Throws [IOException] if it cannot load. */
    private val words: suspend () -> (String) -> Boolean,
    private val preferences: AppPreferences,
    private val client: HttpClient = HttpClient(Allowlist.DAREK),
    private val now: () -> Instant = Instant::now,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
    private val key: PublicKey = FuchyKey.maksForge,
) {
    private val running = Mutex()

    suspend fun catalogue(): Fetched<Catalogue> =
        when (val result = client.get(CATALOGUE_URL)) {
            is HttpResult.Response -> Fetched.Got(parseCatalogue(result.body))
            else -> failure(result)
        }

    suspend fun download(entry: CatalogueEntry): Fetched<ByteArray> =
        when (val result = client.get(entry.url, maxBytes = MAX_FUCHA_BYTES)) {
            is HttpResult.Response -> Fetched.Got(result.body)
            else -> failure(result)
        }

    /** At most once a day, at process start, and only with an approved fucha other than Darek's own installed. */
    suspend fun daily() {
        val last = preferences.fuchyCheckedAt.first()
        if (last != null && Duration.between(last, now()) < DAY) return
        if (updatable().isEmpty()) return
        val run = run(asked = false)
        log("daily check=${run.label()}")
    }

    /**
     * [asked]: the user asked for it, by voice, so it updates even with automatic updates off. [read]
     * is the page's own copy of `fuchy.json`, so opening the page reads it once.
     */
    suspend fun run(
        asked: Boolean,
        read: List<CatalogueEntry>? = null,
    ): UpdateRun =
        running.withLock {
            val updatable = updatable()
            if (updatable.isEmpty()) return UpdateRun.NothingToUpdate
            val entries =
                read ?: when (val fetched = catalogue()) {
                    is Fetched.Got -> (fetched.value as? Catalogue.Read)?.entries ?: return UpdateRun.Failed
                    Fetched.NoInternet -> return UpdateRun.NoInternet
                    Fetched.Failed -> return UpdateRun.Failed
                }
            preferences.markFuchyChecked(now())
            val install = asked || preferences.fuchyAutoUpdate.first()
            val updated = mutableListOf<Fucha>()
            val waiting = mutableListOf<Fucha>()
            for (installed in updatable) {
                val entry = entries.firstOrNull { it.id == installed.fucha.id } ?: continue
                if (entry.version <= installed.fucha.version || entry.needsNewerDarek) continue
                when (val outcome = update(entry, install)) {
                    is Outcome.Updated -> updated += outcome.fucha
                    is Outcome.Waits -> waiting += installed.fucha
                    Outcome.NoInternet -> return UpdateRun.NoInternet
                    Outcome.Failed -> return UpdateRun.Failed
                }
            }
            UpdateRun.Done(updated, waiting)
        }

    private sealed interface Outcome {
        data class Updated(
            val fucha: Fucha,
        ) : Outcome

        data object Waits : Outcome

        data object NoInternet : Outcome

        data object Failed : Outcome
    }

    private suspend fun update(
        entry: CatalogueEntry,
        install: Boolean,
    ): Outcome {
        val bytes =
            when (val fetched = download(entry)) {
                is Fetched.Got -> fetched.value
                Fetched.NoInternet -> return Outcome.NoInternet
                Fetched.Failed -> return Outcome.Failed
            }
        val knows =
            try {
                words()
            } catch (e: IOException) {
                return Outcome.Failed.also { Log.e(TAG, "model failed to load, ${entry.id} not checked", e) }
            }
        val checked = withContext(Dispatchers.Default) { checked(bytes, knows = knows, key = key) }
        val candidate = (checked as? Checked.Accepted)?.fucha?.takeIf { it.id == entry.id } ?: return Outcome.Waits
        val plan = planInstall(candidate, fuchy.installed.first())
        if (!install || updateDecision(plan) != UpdateDecision.Automatic) return Outcome.Waits
        return try {
            fuchy.install(bytes, plan)
            Outcome.Updated(candidate)
        } catch (e: IOException) {
            Outcome.Failed.also { Log.e(TAG, "could not save ${entry.id}", e) }
        }
    }

    private suspend fun updatable(): List<Installed> =
        fuchy.installed.first().filter {
            !it.fucha.bundled &&
                it.fucha.approved
        }

    private fun failure(result: HttpResult): Fetched<Nothing> =
        when (result) {
            HttpResult.TimedOut, is HttpResult.Network -> Fetched.NoInternet
            else -> Fetched.Failed.also { log("request failed $result") }
        }

    private companion object {
        const val TAG = "DarekFuchy"
        const val MAX_FUCHA_BYTES = 256 * 1024
        val DAY: Duration = Duration.ofDays(1)
    }
}

private fun UpdateRun.label(): String =
    when (this) {
        UpdateRun.NothingToUpdate -> "nothing"
        UpdateRun.NoInternet -> "no_internet"
        UpdateRun.Failed -> "failed"
        is UpdateRun.Done -> "updated=${updated.size} waiting=${waiting.size}"
    }

/** What *„Darek, zaktualizuj fuchy"* says it did (phase-F.md section 7). */
fun updateLine(run: UpdateRun): Line =
    when (run) {
        UpdateRun.NothingToUpdate -> Line("Nie mam żadnej fuchy do aktualizowania.")
        UpdateRun.NoInternet -> Line("Nie mogę sprawdzić fuch, nie mam internetu.")
        UpdateRun.Failed -> Line("Nie udało się sprawdzić fuch.")
        is UpdateRun.Done -> {
            val updated = run.updated.joinToString(", ") { it.name }
            val waiting = run.waiting.joinToString(", ") { it.name }
            when {
                run.updated.isEmpty() && run.waiting.isEmpty() -> Line("Wszystkie fuchy są aktualne.")
                run.waiting.isEmpty() -> Line("Zaktualizowałem: $updated.", "Zaktualizowałam: $updated.")
                run.updated.isEmpty() -> Line("Na twoją zgodę w aplikacji czeka: $waiting.")
                else ->
                    Line(
                        "Zaktualizowałem: $updated. Na twoją zgodę w aplikacji czeka: $waiting.",
                        "Zaktualizowałam: $updated. Na twoją zgodę w aplikacji czeka: $waiting.",
                    )
            }
        }
    }
