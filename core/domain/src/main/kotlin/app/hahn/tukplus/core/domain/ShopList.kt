package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.model.LatLon
import java.time.Instant
import java.time.ZonedDateTime

/**
 * One shop in the Eat list, with the values that we calculate from it.
 *
 * @property distanceMetres Estimated road distance from the user. Null when not known.
 * @property forYouScore Score from `recommendations/foryou`. 0 when the shop has no score.
 * @property freeDelivery True when the shop is in the "free delivery" preset.
 */
data class ShopListItem(
    val business: Business,
    val categories: ShopCategories,
    val openState: OpenState,
    val distanceMetres: Int?,
    val forYouScore: Double,
    val freeDelivery: Boolean,
) {
    /** Lowercase text for the text filter: name, categories (with hidden keywords) and search terms. */
    val searchText: String by lazy {
        listOf(business.name, business.data?.categories.orEmpty(), business.data?.searchTerms.orEmpty())
            .joinToString(" ").lowercase()
    }
}

/** Sort orders of the Eat list. */
enum class EatSort { FOR_YOU, DISTANCE, NAME, NEWEST }

/**
 * Filters and sort order of the Eat list.
 *
 * @property fulfilment Values of `fulfilment_options`: "delivery", "take-away", "dine-in".
 *   A shop stays when it has one or more of them. An empty set keeps all shops.
 * @property maxDistanceKm Shops farther than this are removed. Shops with no known distance stay.
 */
data class EatQuery(
    val text: String = "",
    val preset: EatPresetKind? = null,
    val openNowOnly: Boolean = false,
    val fulfilment: Set<String> = emptySet(),
    val maxDistanceKm: Double? = null,
    val sort: EatSort = EatSort.FOR_YOU,
)

/** The Eat list: open shops first, then closed shops. Each part is sorted. */
data class EatResult(val open: List<ShopListItem>, val closed: List<ShopListItem>)

/** Makes the Eat list from the eatery list (api-reference §5). */
object ShopList {
    private val FREE_DELIVERY_FRUITS = listOf("p_", "f_", "thai", "r_")

    /**
     * Makes list items from [shops]. Shops that are hidden, deleted, not active, or that have
     * no Commerce workflow are removed. [forYou] maps a business id to its "for you" score.
     */
    fun build(
        shops: List<Business>,
        now: ZonedDateTime,
        userLocation: LatLon?,
        forYou: Map<String, Double>,
    ): List<ShopListItem> = shops.mapNotNull { shop ->
        if (shop.data?.hidden == true || shop.isDeleted || !shop.isActive) return@mapNotNull null
        val commerce = shop.commerceWorkflow ?: return@mapNotNull null
        ShopListItem(
            business = shop,
            categories = Categories.parse(shop.data?.categories),
            openState = OpenHours.state(shop, now),
            distanceMetres = distance(shop, userLocation),
            forYouScore = forYou[shop.id] ?: 0.0,
            freeDelivery = isFreeDelivery(commerce.data?.freeDeliveryOver, commerce.data?.fruit),
        )
    }

    /**
     * The rule of the web preset "*free-delivery": the shop sets `free_delivery_over`, or its
     * package code (`fruit`) starts with "p_", "f_", "thai" or "r_".
     */
    fun isFreeDelivery(freeDeliveryOver: Int?, fruit: String?): Boolean =
        (freeDeliveryOver ?: 0) > 0 || FREE_DELIVERY_FRUITS.any { fruit?.startsWith(it) == true }

    private fun distance(shop: Business, user: LatLon?): Int? {
        val lat = shop.lat ?: return null
        val lon = shop.lon ?: return null
        if (user == null || (lat == 0.0 && lon == 0.0)) return null
        return Distance.estimatedRoadMetres(user, LatLon(lat, lon))
    }

    /**
     * Applies [query] to [items].
     *
     * The text filter is a lowercase substring match on the name, the categories text and the
     * search terms. The categories text includes hidden keywords, so "buy1get1" finds "_buy1get1".
     * A preset changes the query: FOR_YOU sets the sort, FREE_DELIVERY keeps only free delivery
     * shops, OPEN_NOW keeps only open shops.
     */
    fun apply(items: List<ShopListItem>, query: EatQuery): EatResult {
        val text = query.text.trim().lowercase()
        val sort = if (query.preset == EatPresetKind.FOR_YOU) EatSort.FOR_YOU else query.sort
        val freeOnly = query.preset == EatPresetKind.FREE_DELIVERY
        val openOnly = query.openNowOnly || query.preset == EatPresetKind.OPEN_NOW
        val maxMetres = query.maxDistanceKm?.let { it * 1000 }

        val kept = items.filter { item ->
            (text.isEmpty() || item.searchText.contains(text)) &&
                (!freeOnly || item.freeDelivery) &&
                (query.fulfilment.isEmpty() || item.fulfilmentOptions().any { it in query.fulfilment }) &&
                (maxMetres == null || item.distanceMetres == null || item.distanceMetres <= maxMetres)
        }.sortedWith(comparator(sort))

        val (open, closed) = kept.partition { it.openState.isOpen }
        return EatResult(open, if (openOnly) emptyList() else closed)
    }

    private fun ShopListItem.fulfilmentOptions(): List<String> =
        business.commerceWorkflow?.data?.fulfilmentOptions.orEmpty()

    private val byName: Comparator<ShopListItem> = compareBy(String.CASE_INSENSITIVE_ORDER) { it.business.name }

    /** Unknown distances go last. */
    private val byDistance: Comparator<ShopListItem> = compareBy(nullsLast()) { it.distanceMetres }

    /** The comparator for [sort]. Ties are sorted by name. */
    fun comparator(sort: EatSort): Comparator<ShopListItem> = when (sort) {
        EatSort.FOR_YOU -> compareByDescending<ShopListItem> { it.forYouScore }.then(byDistance).then(byName)
        EatSort.DISTANCE -> byDistance.then(byName)
        EatSort.NAME -> byName
        EatSort.NEWEST -> compareBy<ShopListItem, Instant?>(nullsLast(reverseOrder())) {
            OpenHours.parseInstant(it.business.createdAt)
        }.then(byName)
    }
}
