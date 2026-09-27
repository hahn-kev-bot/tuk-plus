package app.hahn.tukplus.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hahn.tukplus.core.data.BrowseRepository
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.data.SearchRepository
import app.hahn.tukplus.core.domain.LocalSearch
import app.hahn.tukplus.core.domain.ShopList
import app.hahn.tukplus.core.domain.ShopListItem
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.AutocompleteEntry
import app.hahn.tukplus.core.model.MenuItemSearchHit
import app.hahn.tukplus.platform.LocationProvider
import app.hahn.tukplus.ui.common.chiangMaiTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject

data class ServerResults(
    val shops: List<AutocompleteEntry> = emptyList(),
    val menuHits: List<MenuItemSearchHit> = emptyList(),
    val status: Cached<Unit> = Cached.empty(),
)

data class SearchUiState(
    val text: String = "",
    /** From the cached shop list. They show at once. */
    val local: List<ShopListItem> = emptyList(),
    /** Shops that the server found and that are not in [local]. */
    val server: ServerResults = ServerResults(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    browse: BrowseRepository,
    private val search: SearchRepository,
    location: LocationProvider,
    clock: Clock,
    private val log: TukLog,
) : ViewModel() {
    private val text = MutableStateFlow(savedState.get<String>("q").orEmpty())

    private val items = combine(browse.eateries.state, location.location, chiangMaiTicker(clock)) { eateries, here, now ->
        ShopList.build(eateries.data.orEmpty(), now, here, emptyMap())
    }

    private val server: Flow<ServerResults> = text.debounce(400).flatMapLatest { raw ->
        val q = raw.trim()
        if (q.length < SearchRepository.MIN_SERVER_CHARS) return@flatMapLatest flowOf(ServerResults())
        log.i("ui", "search", "chars" to q.length)
        val shops = search.shops(q).also { it.start() }
        val menu = search.menuItems(q).also { it.start() }
        combine(shops.state, menu.state) { s, m ->
            ServerResults(
                shops = s.data?.businesses.orEmpty().filter { it.hidden != true && !it.pic.isNullOrBlank() },
                menuHits = m.data.orEmpty().sortedByDescending { it.count ?: 0 },
                status = Cached.combineStatus(listOf(s, m)),
            )
        }
    }

    val state: StateFlow<SearchUiState> = combine(text, items, server) { t, list, s ->
        val local = LocalSearch.shops(list, t)
        val localIds = local.map { it.business.id }.toSet()
        SearchUiState(t, local, s.copy(shops = s.shops.filter { it.value !in localIds }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState(text = text.value))

    fun setText(value: String) {
        text.value = value
    }
}
