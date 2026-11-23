package app.darek.ui.fuchy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.darek.MainApplication
import app.darek.data.AppPreferences
import app.darek.fuchy.Fuchy
import app.darek.fuchy.FuchyPage
import app.darek.fuchy.Listing
import app.darek.fuchy.PageFetch
import app.darek.fuchy.PageRow
import app.darek.fuchy.pageRows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PageLoad {
    data object Loading : PageLoad

    data object NoInternet : PageLoad

    data object Failed : PageLoad

    data object NewerFormat : PageLoad

    data class Listed(
        val listings: List<Listing>,
    ) : PageLoad
}

/** [waiting] are the IDs whose update widens what the fucha may do, so automatic updates left them for the user. */
data class PageUiState(
    val load: PageLoad = PageLoad.Loading,
    val rows: List<PageRow> = emptyList(),
    val autoUpdated: List<String> = emptyList(),
    val waiting: Set<String> = emptySet(),
)

private data class Fetched(
    val load: PageLoad = PageLoad.Loading,
    val autoUpdated: List<String> = emptyList(),
    val waiting: Set<String> = emptySet(),
)

/** *Strona z fuchami* (phase-F.md section 7): opening it is the first time a fresh install connects anywhere. */
class PageViewModel(
    private val page: FuchyPage,
    fuchy: Fuchy,
    private val preferences: AppPreferences,
) : ViewModel() {
    private val fetched = MutableStateFlow(Fetched())

    val state: StateFlow<PageUiState> =
        combine(fetched, fuchy.installed, preferences.showProfanity) { fetched, installed, showProfanity ->
            val listings = (fetched.load as? PageLoad.Listed)?.listings.orEmpty()
            PageUiState(
                fetched.load,
                pageRows(listings, installed, showProfanity),
                fetched.autoUpdated,
                fetched.waiting,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageUiState())

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        fetched.value = Fetched()
        viewModelScope.launch {
            fetched.value =
                when (val fetch = page.read()) {
                    PageFetch.NoInternet -> Fetched(PageLoad.NoInternet)
                    PageFetch.Failed -> Fetched(PageLoad.Failed)
                    PageFetch.NewerFormat -> Fetched(PageLoad.NewerFormat)
                    is PageFetch.Listed -> {
                        val listed = PageLoad.Listed(fetch.listings)
                        if (preferences.autoUpdate.first()) {
                            fetched.value = Fetched(listed)
                            val done = page.update(fetch.listings)
                            Fetched(listed, done.updated.map { it.name }, done.waiting.map { it.id }.toSet())
                        } else {
                            Fetched(listed)
                        }
                    }
                }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MainApplication
                    PageViewModel(app.container.page, app.container.fuchy, app.preferences)
                }
            }
    }
}
