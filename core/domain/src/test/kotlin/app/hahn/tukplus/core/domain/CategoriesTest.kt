package app.hahn.tukplus.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoriesTest {
    @Test
    fun `splits cuisines, badges and keywords`() {
        val result = Categories.parse("American, Diner, Breakfast, _butterisbetter, _pancakes, _TOP RATED*, new*,, ")
        assertEquals(listOf("American", "Diner", "Breakfast"), result.cuisines)
        assertEquals(listOf("TOP RATED", "New"), result.badges)
        assertEquals(listOf("butterisbetter", "pancakes"), result.keywords)
        assertEquals("American", result.mainCuisine)
    }

    @Test
    fun `empty text gives nothing`() {
        assertEquals(ShopCategories.EMPTY, Categories.parse(null))
        assertEquals(ShopCategories.EMPTY, Categories.parse(" , ,"))
        assertNull(Categories.parse("_x").mainCuisine)
    }

    @Test
    fun `recorded shops have no empty or marked cuisine`() {
        val all = Fixtures.eateries.map { Categories.parse(it.data?.categories) }
        assertTrue(all.count { it.mainCuisine != null } > 200)
        for (c in all) {
            assertTrue(c.cuisines.none { it.isBlank() || it.startsWith("_") || it.endsWith("*") }, c.toString())
            assertTrue(c.badges.none { it.contains('*') || it.startsWith("_") }, c.toString())
        }
    }
}
