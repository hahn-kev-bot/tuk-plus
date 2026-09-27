package app.hahn.tukplus.ui.home

import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hahn.tukplus.core.data.BrowseRepository
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.data.RecentShops
import app.hahn.tukplus.core.domain.EatQuery
import app.hahn.tukplus.core.domain.EatSort
import app.hahn.tukplus.core.domain.HomeRules
import app.hahn.tukplus.core.domain.ShopList
import app.hahn.tukplus.core.domain.ShopListItem
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.NewShop
import app.hahn.tukplus.core.model.PageBlobs
import app.hahn.tukplus.core.model.PageRow
import app.hahn.tukplus.core.network.TukApi
import app.hahn.tukplus.platform.LocationProvider
import app.hahn.tukplus.ui.common.chiangMaiTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import javax.inject.Inject
import kotlin.random.Random

data class HomeUiState(
    val rows: List<PageRow> = emptyList(),
    val recent: List<ShopListItem> = emptyList(),
    val newShops: List<NewShop> = emptyList(),
    val topEats: List<ShopListItem> = emptyList(),
    /** Age and status of all data on the screen together. */
    val status: Cached<Unit> = Cached.empty(),
) {
    val isEmpty: Boolean get() = rows.isEmpty() && topEats.isEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val browse: BrowseRepository,
    recent: RecentShops,
    location: LocationProvider,
    clock: Clock,
    private val log: TukLog,
) : ViewModel() {
    /** Rows with "randomise" keep one order for this screen, like one page load on the web. */
    private val shuffleSeed = Random.nextLong()
    private var readyLogged = false

    init {
        location.update()
    }

    private val pages = combine(browse.homePages.map { it.state }) { it.toList() }

    private val shops = combine(browse.eateries.state, browse.forYou.state, location.location, chiangMaiTicker(clock)) { eateries, forYou, here, now ->
        val scores = forYou.data.orEmpty().associate { it.key to (it.score ?: 0.0) }
        Triple(ShopList.build(eateries.data.orEmpty(), now, here, scores), now, listOf(eateries, forYou))
    }

    val state: StateFlow<HomeUiState> = combine(pages, shops, browse.newShops.state, recent.ids) { pageStates, (items, now, shopStates), newShops, recentIds ->
        val byId = items.associateBy { it.business.id }
        val openByHandle = items.mapNotNull { item ->
            item.business.data?.premiumLink?.removePrefix("@")?.lowercase()?.let { it to item.openState.isOpen }
        }.toMap()
        val rows = pageStates.mapIndexed { i, cached -> PageBlobs.toRow(TukApi.HOME_PAGES[i], cached.data.orEmpty()) }
            .filter { it.page != "Home_Business" } // Sign-up links for shop owners; not for customers.
        HomeUiState(
            rows = HomeRules.visibleRows(rows, now, { handle -> openByHandle[handle.removePrefix("@").lowercase()] }, shuffleSeed),
            recent = recentIds.mapNotNull { byId[it] }.take(10),
            newShops = newShops.data.orEmpty().take(10),
            topEats = ShopList.apply(items, EatQuery(sort = EatSort.FOR_YOU)).open.take(20),
            status = Cached.combineStatus(pageStates + shopStates + newShops),
        )
    }.onEach { state ->
        // Phase 1 goal: Home shows in less than 1 s from the cache (docs/phases/phase-1.md).
        if (!readyLogged && !state.isEmpty) {
            readyLogged = true
            log.i(
                "perf", "home_ready",
                "ms_since_process_start" to (SystemClock.uptimeMillis() - Process.getStartUptimeMillis()),
                "data_age_s" to state.status.age(java.time.Instant.now())?.seconds,
                "status" to state.status.status.toString(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() {
        log.i("ui", "refresh", "screen" to "home")
        browse.refreshAll()
    }
}
