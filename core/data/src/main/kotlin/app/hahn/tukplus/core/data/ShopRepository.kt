package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.model.CommerceDelivery
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.core.network.Endpoints
import java.time.Instant

/** Menus, delivery fleets and single shops (api-reference §6). */
class ShopRepository(
    private val store: ResourceStore,
    private val endpoints: Endpoints,
    private val browse: BrowseRepository,
) {
    /**
     * The workflows of a shop with the menu blobs. The cached menu is also outdated when
     * the shop list shows a newer `updated_at` for the Commerce workflow than the menu has.
     */
    fun menu(businessId: String): CachedResource<List<Workflow>> = store.resource(
        endpoints.workflowsForBusiness(businessId),
        Policies.MENU,
        isOutdated = { workflows ->
            val listed = browse.business(businessId)?.commerceWorkflow?.updatedAt.toInstantOrNull()
            val cached = workflows.firstOrNull { it.workflowTypeName == Workflow.TYPE_COMMERCE }?.updatedAt.toInstantOrNull()
            listed != null && cached != null && listed.isAfter(cached)
        },
    )

    fun fleet(commerceWorkflowId: String): CachedResource<CommerceDelivery> =
        store.resource(endpoints.commerceDelivery(commerceWorkflowId), Policies.FLEET)

    /** A shop that is not in the cached list (for example a link to a hidden shop). */
    fun business(businessId: String): CachedResource<Business> = store.resource(endpoints.business(businessId), Policies.BUSINESS)

    /** The shop from the list when it is there. Does not use the network. */
    fun listedBusiness(businessId: String): Business? = browse.business(businessId)

    /** Finds the business id of a handle: first in the cached list, else with `short_link` (cached 7 days). */
    suspend fun resolveHandle(handle: String): String? =
        browse.businessIdForHandle(handle)
            ?: store.resource(endpoints.shortLink(handle), Policies.SHORT_LINK).get().data?.businessId
}

internal fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }
