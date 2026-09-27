package app.hahn.tukplus.ui.shop

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hahn.tukplus.core.data.BrowseRepository
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.data.RecentShops
import app.hahn.tukplus.core.data.ShopRepository
import app.hahn.tukplus.core.domain.MenuRules
import app.hahn.tukplus.core.domain.MenuSection
import app.hahn.tukplus.core.domain.OpenHours
import app.hahn.tukplus.core.domain.OpenState
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuParser
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.platform.MenuView
import app.hahn.tukplus.platform.MenuViewPreference
import app.hahn.tukplus.ui.common.chiangMaiTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Clock
import javax.inject.Inject

data class ShopUiState(
    val business: Business? = null,
    val openState: OpenState? = null,
    val todayText: String = "",
    val menu: Menu? = null,
    val sections: List<MenuSection> = emptyList(),
    val preface: String? = null,
    val menuSearch: String = "",
    val view: MenuView = MenuView.LIST,
    val status: Cached<Unit> = Cached.empty(),
    /** The handle or id does not lead to a shop. */
    val notFound: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShopViewModel @Inject constructor(
    savedState: SavedStateHandle,
    browse: BrowseRepository,
    private val shops: ShopRepository,
    private val recent: RecentShops,
    private val menuView: MenuViewPreference,
    clock: Clock,
    private val log: TukLog,
) : ViewModel() {
    private val businessId = MutableStateFlow(savedState.get<String>("id"))
    private val notFound = MutableStateFlow(false)
    private val menuSearch = MutableStateFlow(savedState.get<String>("q").orEmpty())
    private val openedAt = SystemClock.uptimeMillis()
    private var readyLogged = false

    init {
        val handle = savedState.get<String>("handle")
        if (businessId.value == null && handle != null) {
            viewModelScope.launch {
                val id = shops.resolveHandle(handle)
                log.i("shop", "handle", "handle" to handle, "found" to (id != null))
                if (id == null) notFound.value = true else businessId.value = id
            }
        }
        viewModelScope.launch {
            businessId.filterNotNull().collect { id ->
                recent.opened(id)
                log.i("shop", "open", "business_id" to id)
                shops.menu(id).start()
            }
        }
    }

    /** The shop from the list, else from `businesses/{id}` (for shops that are not in the list). */
    private val business = businessId.filterNotNull().flatMapLatest { id ->
        browse.eateries.state.flatMapLatest { list ->
            val listed = list.data?.firstOrNull { it.id == id }
            if (listed != null) flowOf(Cached(listed, list.fetchedAt, list.status)) else shops.business(id).also { it.start() }.state
        }
    }

    private val menu = businessId.filterNotNull().flatMapLatest { id -> shops.menu(id).state }.map { cached ->
        val commerce = cached.data?.firstOrNull { it.workflowTypeName == Workflow.TYPE_COMMERCE }
        commerce?.let { wf ->
            // Warm the delivery fleet for the cart (phase 2).
            shops.fleet(wf.id).start()
        }
        cached to commerce?.let(MenuParser::parse)
    }

    private val searchAndView = combine(menuSearch, menuView.view) { search, view -> search to view }

    val state: StateFlow<ShopUiState> = combine(business, menu, searchAndView, chiangMaiTicker(clock), notFound) { b, (menuCached, parsed), (search, view), now, missing ->
        val shop = b.data
        val visible = parsed?.let { MenuRules.visibleEntries(it, now) }.orEmpty()
        val filtered = if (search.isBlank()) visible else visible.filter { MenuRules.matches(it, search) }
        ShopUiState(
            business = shop,
            openState = shop?.let { OpenHours.state(it, now) },
            todayText = shop?.let { OpenHours.todayText(it, now) }.orEmpty(),
            menu = parsed,
            sections = parsed?.let { MenuRules.sections(it, filtered) }.orEmpty(),
            preface = parsed?.preface?.let { (it["en"] as? JsonPrimitive)?.contentOrNull ?: (it["preface"] as? JsonPrimitive)?.contentOrNull },
            menuSearch = search,
            view = view,
            status = Cached.combineStatus(listOf(menuCached)),
            notFound = missing,
        )
    }.onEach { state ->
        // Phase 1 goal: a shop opens at once from the cache (docs/phases/phase-1.md).
        if (!readyLogged && state.menu != null) {
            readyLogged = true
            log.i(
                "perf", "shop_ready",
                "business_id" to state.business?.id,
                "ms" to (SystemClock.uptimeMillis() - openedAt),
                "menu_age_s" to state.status.age(java.time.Instant.now())?.seconds,
                "items" to state.menu.entries.size,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShopUiState(menuSearch = menuSearch.value))

    fun setView(view: MenuView) {
        log.i("ui", "menu_view", "view" to view.name)
        menuView.set(view)
    }

    fun setMenuSearch(text: String) {
        menuSearch.value = text
    }

    fun refresh() {
        val id = businessId.value ?: return
        log.i("ui", "refresh", "screen" to "shop")
        shops.menu(id).refresh()
    }
}
