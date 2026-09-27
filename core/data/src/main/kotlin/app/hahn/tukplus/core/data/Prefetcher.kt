package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.domain.TileAction
import app.hahn.tukplus.core.domain.TileActions
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.PageBlobs
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.core.network.TukApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Loads data before the user asks for it (PLAN.md §5.4).
 *
 * - At app start: opens the HTTP/2 connection early, then loads the shop list, Home
 *   and Eat pages and recommendations, all in parallel.
 * - After the shop list: loads the menus (and delivery fleets) of recent shops and of the
 *   "What's HOT" row, at most [MAX_WARM_MENUS], [PARALLEL] at a time. Only when
 *   [mayUseDataFreely] is true (for example on Wi-Fi).
 * - When a shop card stays on screen: loads that menu ([onShopVisible]).
 */
class Prefetcher(
    private val api: TukApi,
    private val browse: BrowseRepository,
    private val shops: ShopRepository,
    private val recent: RecentShops,
    private val scope: CoroutineScope,
    private val log: TukLog,
    private val mayUseDataFreely: () -> Boolean,
) {
    private val gate = Semaphore(PARALLEL)

    fun onAppStart() {
        scope.launch { api.version() } // Opens the connection; the other calls reuse it.
        browse.startAll()
        scope.launch {
            browse.eateries.get()
            if (mayUseDataFreely()) warmMenus() else log.i("prefetch", "skip_menus", "reason" to "metered network")
        }
    }

    fun onShopVisible(businessId: String) {
        scope.launch { warmShop(businessId) }
    }

    private suspend fun warmMenus() {
        val hotRow = browse.homePages.getOrNull(HOT_PAGE_INDEX)?.get()?.data.orEmpty()
        val hot = PageBlobs.toRow(TukApi.HOME_PAGES[HOT_PAGE_INDEX], hotRow).tiles
            .mapNotNull { (TileActions.parse(it.tag) as? TileAction.ShopHandle)?.let { a -> browse.businessIdForHandle(a.handle) } }
        val ids = (recent.ids.value + hot).distinct().take(MAX_WARM_MENUS)
        log.i("prefetch", "menus_start", "count" to ids.size)
        ids.map { id -> scope.launch { warmShop(id) } }.forEach { it.join() }
        log.i("prefetch", "menus_done", "count" to ids.size)
    }

    private suspend fun warmShop(businessId: String) = gate.withPermit {
        val menu = shops.menu(businessId)
        val workflows = if (menu.needsRefresh()) menu.get().data else menu.state.value.data
        val commerce = workflows?.firstOrNull { it.workflowTypeName == Workflow.TYPE_COMMERCE } ?: return@withPermit
        val fleet = shops.fleet(commerce.id)
        if (fleet.needsRefresh()) fleet.get()
    }

    companion object {
        const val MAX_WARM_MENUS = 20
        const val PARALLEL = 4
        /** Home_2 is "What's HOT" (api-reference §4). */
        private const val HOT_PAGE_INDEX = 1
    }
}
