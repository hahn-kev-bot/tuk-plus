package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.domain.Fixtures.monday
import app.hahn.tukplus.core.domain.Fixtures.shop
import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.model.WorkflowData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShopListTest {
    private val user = LatLon(18.0, 99.0)
    private val open = mapOf("mon" to "0000-0000")
    private val closed = mapOf("mon" to "")

    private val shops = listOf(
        shop("a", "Alpha Pizza", hours = open, categories = "Italian, _buy1get1", lat = 18.02, lon = 99.0,
            createdAt = "2024-01-01T00:00:00Z", commerce = WorkflowData(fulfilmentOptions = listOf("delivery"), fruit = "r_10_0")),
        shop("b", "bravo Burgers", hours = open, categories = "American, _15%", searchTerms = "smash", lat = 18.01, lon = 99.0,
            createdAt = "2025-01-01T00:00:00Z", commerce = WorkflowData(fulfilmentOptions = listOf("take-away"), fruit = "x_1")),
        shop("c", "Charlie Thai", hours = closed, categories = "Thai", lat = 18.2, lon = 99.0,
            commerce = WorkflowData(fulfilmentOptions = listOf("dine-in", "delivery"), freeDeliveryOver = 500)),
        shop("d", "Delta", hours = open, categories = "Thai"),
        shop("hidden", "Hidden", hidden = true),
        shop("deleted", "Deleted", isDeleted = true),
        shop("inactive", "Inactive", isActive = false),
        shop("nocommerce", "No commerce", commerce = null),
    )
    private val items = ShopList.build(shops, monday(12), user, mapOf("b" to 10.0, "c" to 20.0, "d" to 10.0))
    private fun ids(list: List<ShopListItem>) = list.map { it.business.id }

    @Test
    fun `build removes shops that cannot be shown and calculates values`() {
        assertEquals(listOf("a", "b", "c", "d"), ids(items))
        val a = items.first()
        assertEquals("Italian", a.categories.mainCuisine)
        assertTrue(a.openState.isOpen)
        assertEquals(Distance.estimatedRoadMetres(user, LatLon(18.02, 99.0)), a.distanceMetres)
        assertEquals(0.0, a.forYouScore)
        assertEquals(null, items.last().distanceMetres)
        assertEquals(OpenState.ClosedToday, items[2].openState)
        assertEquals(listOf(true, false, true, false), items.map { it.freeDelivery })
        assertTrue(ShopList.build(shops, monday(12), null, emptyMap()).all { it.distanceMetres == null })
    }

    @Test
    fun `free delivery rule`() {
        for (fruit in listOf("p_1", "f_20_10", "thai", "r_10_0")) assertTrue(ShopList.isFreeDelivery(null, fruit), fruit)
        assertTrue(ShopList.isFreeDelivery(300, null))
        assertFalse(ShopList.isFreeDelivery(0, "x_1"))
        assertFalse(ShopList.isFreeDelivery(null, null))
    }

    @Test
    fun `sort orders`() {
        val forYou = ShopList.apply(items, EatQuery(sort = EatSort.FOR_YOU))
        // Score first, then distance (b is nearer than d, whose distance is unknown).
        assertEquals(listOf("b", "d", "a"), ids(forYou.open))
        assertEquals(listOf("c"), ids(forYou.closed))
        assertEquals(listOf("b", "a", "d"), ids(ShopList.apply(items, EatQuery(sort = EatSort.DISTANCE)).open))
        assertEquals(listOf("a", "b", "d"), ids(ShopList.apply(items, EatQuery(sort = EatSort.NAME)).open))
        assertEquals(listOf("b", "a", "d"), ids(ShopList.apply(items, EatQuery(sort = EatSort.NEWEST)).open))
        // The FOR_YOU preset sets the sort.
        assertEquals(listOf("b", "d", "a"), ids(ShopList.apply(items, EatQuery(sort = EatSort.NAME, preset = EatPresetKind.FOR_YOU)).open))
    }

    @Test
    fun `filters`() {
        assertEquals(listOf("a"), ids(ShopList.apply(items, EatQuery(text = "BUY1get1")).open))
        assertEquals(listOf("b"), ids(ShopList.apply(items, EatQuery(text = "15%")).open))
        assertEquals(listOf("b"), ids(ShopList.apply(items, EatQuery(text = " smash ")).open))
        val thai = ShopList.apply(items, EatQuery(text = "thai", sort = EatSort.NAME))
        assertEquals(listOf("d"), ids(thai.open))
        assertEquals(listOf("c"), ids(thai.closed))
        assertEquals(emptyList(), ShopList.apply(items, EatQuery(text = "thai", openNowOnly = true)).closed)
        assertEquals(emptyList(), ShopList.apply(items, EatQuery(preset = EatPresetKind.OPEN_NOW)).closed)
        val free = ShopList.apply(items, EatQuery(preset = EatPresetKind.FREE_DELIVERY))
        assertEquals(setOf("a", "c"), ids(free.open + free.closed).toSet())
        val delivery = ShopList.apply(items, EatQuery(fulfilment = setOf("delivery")))
        assertEquals(setOf("a", "c"), ids(delivery.open + delivery.closed).toSet())
        val near = ShopList.apply(items, EatQuery(maxDistanceKm = 3.0))
        // c is about 29 km away. d has no location, so it stays.
        assertEquals(setOf("a", "b", "d"), ids(near.open + near.closed).toSet())
    }

    @Test
    fun `recorded eatery list`() {
        val live = ShopList.build(Fixtures.eateries, monday(12), LatLon.CHIANG_MAI, emptyMap())
        assertTrue(live.size > 200, "only ${live.size} shops")
        for (text in listOf("buy1get1", "american", "pizza", "thai")) {
            val result = ShopList.apply(live, EatQuery(text = text))
            assertTrue(result.open.size + result.closed.size > 0, "no shop for $text")
        }
        val all = ShopList.apply(live, EatQuery(sort = EatSort.DISTANCE))
        assertEquals(live.size, all.open.size + all.closed.size)
        assertTrue(all.open.isNotEmpty() && all.closed.isNotEmpty())
        val distances = all.open.mapNotNull { it.distanceMetres }
        assertEquals(distances.sorted(), distances)
        assertTrue(live.count { it.freeDelivery } > 0)
    }
}
