package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.domain.Fixtures.monday
import app.hahn.tukplus.core.domain.Fixtures.shop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalSearchTest {
    private val open = mapOf("mon" to "0000-0000")
    private val closed = mapOf("mon" to "")
    private val items = ShopList.build(
        listOf(
            shop("1", "The Pizza Place", hours = open),
            shop("2", "Pizzeria Uno", hours = closed),
            shop("3", "Pizza Mania", hours = open),
            shop("4", "Apizza", hours = open),
            shop("5", "Burger Hut", hours = open, categories = "American, Pizza"),
            shop("6", "Noodles", hours = open, searchTerms = "pizza bread"),
            shop("7", "pizza Zone", hours = closed),
            shop("8", "Salad", hours = open),
            shop("9", "Big-Pizza", hours = closed),
        ),
        monday(12), null, emptyMap(),
    )

    @Test
    fun `ranks matches`() {
        val result = LocalSearch.shops(items, "PIZZA").map { it.business.name }
        assertEquals(
            listOf("Pizza Mania", "pizza Zone", "The Pizza Place", "Big-Pizza", "Apizza", "Burger Hut", "Noodles"),
            result,
        )
    }

    @Test
    fun `limit and blank text`() {
        assertEquals(2, LocalSearch.shops(items, "pizza", limit = 2).size)
        assertEquals(emptyList(), LocalSearch.shops(items, " "))
        assertEquals(emptyList(), LocalSearch.shops(items, "sushi"))
    }

    @Test
    fun `recorded eatery list`() {
        val live = ShopList.build(Fixtures.eateries, monday(12), null, emptyMap())
        val result = LocalSearch.shops(live, "pizza")
        assertTrue(result.isNotEmpty())
        assertTrue(result.size <= 30)
    }
}
