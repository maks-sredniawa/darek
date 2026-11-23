package app.darek.ui.fuchy

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.data.AppPreferences
import app.darek.fuchy.Catalogue
import app.darek.fuchy.CatalogueEntry
import app.darek.fuchy.Fetched
import app.darek.fuchy.Fuchy
import app.darek.fuchy.FuchyUpdates
import app.darek.fuchy.Installed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface PageLoad {
    data object Loading : PageLoad

    data class Failed(
        val retrying: Boolean = false,
    ) : PageLoad

    data object NewerFormat : PageLoad

    data class Read(
        val entries: List<CatalogueEntry>,
    ) : PageLoad
}

enum class RowAction { INSTALL, UPDATE, UP_TO_DATE, NEEDS_NEWER }

data class PageRow(
    val entry: CatalogueEntry,
    val installed: Installed?,
    val action: RowAction,
    val downloading: Boolean = false,
    val failed: Boolean = false,
)

data class PageUiState(
    val load: PageLoad = PageLoad.Loading,
    val installed: List<PageRow> = emptyList(),
    val available: List<PageRow> = emptyList(),
    val hiddenProfanity: Int = 0,
)

private data class Downloads(
    val busy: String? = null,
    val failed: String? = null,
)

/** *Fuchy do pobrania*: opening it is the first time a fresh install connects anywhere (phase-F.md section 7). */
class PageViewModel(
    private val fuchy: Fuchy,
    private val updates: FuchyUpdates,
    private val preferences: AppPreferences,
    private val downloadsDir: File,
) : ViewModel() {
    private val load = MutableStateFlow<PageLoad>(PageLoad.Loading)
    private val downloads = MutableStateFlow(Downloads())
    private val opened = Channel<Uri>(Channel.BUFFERED)

    /** A downloaded file, opened in the same install screen as one picked from the phone. */
    val toInstall: Flow<Uri> = opened.receiveAsFlow()

    val state: StateFlow<PageUiState> =
        combine(load, fuchy.installed, preferences.showProfanity, downloads) { load, installed, profanity, downloads ->
            val entries = (load as? PageLoad.Read)?.entries.orEmpty()
            val rows =
                entries.map { entry ->
                    val here = installed.firstOrNull { it.fucha.id == entry.id && !it.fucha.bundled }
                    PageRow(
                        entry = entry,
                        installed = here,
                        action = actionFor(entry, here),
                        downloading = downloads.busy == entry.id,
                        failed = downloads.failed == entry.id,
                    )
                }
            val (mine, rest) = rows.partition { it.installed != null }
            val shown = rest.filter { profanity || !it.entry.profanity }
            PageUiState(
                load = load,
                installed = mine.sortedBy { it.action != RowAction.UPDATE },
                available = shown,
                hiddenProfanity = rest.size - shown.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (load.value is PageLoad.Failed) load.value = PageLoad.Failed(retrying = true)
        viewModelScope.launch {
            load.value =
                when (val fetched = updates.catalogue()) {
                    is Fetched.Got ->
                        when (val catalogue = fetched.value) {
                            is Catalogue.Read -> PageLoad.Read(catalogue.entries)
                            is Catalogue.NewerFormat -> PageLoad.NewerFormat
                            Catalogue.Unreadable -> PageLoad.Failed()
                        }
                    Fetched.NoInternet, Fetched.Failed -> PageLoad.Failed()
                }
            (load.value as? PageLoad.Read)?.let { read ->
                if (preferences.fuchyAutoUpdate.first()) updates.run(asked = false, read = read.entries)
            }
        }
    }

    fun get(entry: CatalogueEntry) {
        if (downloads.value.busy != null) return
        downloads.value = Downloads(busy = entry.id)
        viewModelScope.launch {
            val file =
                when (val fetched = updates.download(entry)) {
                    is Fetched.Got -> save(entry.id, fetched.value)
                    Fetched.NoInternet, Fetched.Failed -> null
                }
            downloads.value = Downloads(failed = entry.id.takeIf { file == null })
            file?.let { opened.send(Uri.fromFile(it)) }
        }
    }

    private suspend fun save(
        id: String,
        bytes: ByteArray,
    ): File? =
        withContext(Dispatchers.IO) {
            try {
                downloadsDir.mkdirs()
                File(downloadsDir, "$id.darek").apply { writeBytes(bytes) }
            } catch (_: IOException) {
                null
            }
        }

    private fun actionFor(
        entry: CatalogueEntry,
        installed: Installed?,
    ): RowAction =
        when {
            installed != null && entry.version <= installed.fucha.version -> RowAction.UP_TO_DATE
            entry.needsNewerDarek -> RowAction.NEEDS_NEWER
            installed != null -> RowAction.UPDATE
            else -> RowAction.INSTALL
        }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    PageViewModel(
                        app.container.fuchy,
                        app.container.updates,
                        app.preferences,
                        File(app.cacheDir, "fuchy-downloads"),
                    )
                }
            }
    }
}
