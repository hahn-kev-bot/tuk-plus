package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.model.Blob
import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.network.Endpoints
import app.hahn.tukplus.core.network.TukApi

/** Home rows, Eat chips, the shop list and recommendations (api-reference §4–§5). */
class BrowseRepository(store: ResourceStore, endpoints: Endpoints) {
    val eateries: CachedResource<List<Business>> = store.resource(endpoints.eateries(), Policies.EATERIES)
    val homePages: List<CachedResource<List<Blob>>> =
        TukApi.HOME_PAGES.map { store.resource(endpoints.pageBlobs(it), Policies.HOME_PAGE) }
    val eatChips: CachedResource<List<Blob>> = store.resource(endpoints.pageBlobs(TukApi.EAT_PAGE), Policies.EAT_CHIPS)
    val newShops = store.resource(endpoints.newShops(), Policies.NEW_SHOPS)
    /** Release 1 has no login yet, so the anonymous list. */
    val forYou = store.resource(endpoints.forYou(null), Policies.FOR_YOU)

    private val all get() = listOf(eateries, eatChips, newShops, forYou) + homePages

    /** Reads the disk cache of everything and refreshes what is stale. All in parallel. */
    fun startAll() = all.forEach { it.start() }

    /** Refreshes everything now (pull-to-refresh). */
    fun refreshAll() = all.map { it.refresh() }

    /** A shop from the cached list, or null. */
    fun business(businessId: String): Business? = eateries.state.value.data?.firstOrNull { it.id == businessId }

    /** The business id of a handle such as "@murka", from the cached list (`data.premium_link`). */
    fun businessIdForHandle(handle: String): String? {
        val wanted = handle.removePrefix("@").lowercase()
        return eateries.state.value.data?.firstOrNull { it.data?.premiumLink?.removePrefix("@")?.lowercase() == wanted }?.id
    }
}
