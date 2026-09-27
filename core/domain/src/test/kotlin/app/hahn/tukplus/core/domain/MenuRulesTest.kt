package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.domain.Fixtures.monday
import app.hahn.tukplus.core.model.LocalizedText
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuCategory
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.MenuItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuRulesTest {
    private fun entry(item: MenuItem) = MenuEntry(item, JsonObject(mapOf("id" to JsonPrimitive(item.id))))
    private fun item(id: String, price: String? = "100", category: String? = "Mains") = MenuItem(id = id, price = price, category = category)
    private fun menu(vararg items: MenuItem, categories: List<MenuCategory> = listOf(MenuCategory("Mains"), MenuCategory("Drinks", en = "Drinks EN"))) =
        Menu("wf", "v1", categories, items.map(::entry), emptyMap(), null, emptyList())
    private fun days(vararg d: String) = JsonArray(d.map(::JsonPrimitive))
    private fun ids(entries: List<MenuEntry>) = entries.map { it.item.id }

    @Test
    fun `hidden, schedule and hour tags`() {
        val m = menu(
            item("plain"),
            item("hidden").copy(hidden = true),
            item("monday").copy(schedule = days("mon", "tue")),
            item("mondayFull").copy(schedule = days("Monday")),
            item("friday").copy(schedule = days("fri")),
            item("emptySchedule").copy(schedule = JsonArray(emptyList())),
            item("lunch").copy(tags = listOf("Vegan", "*11-15")),
            item("dinner").copy(tags = listOf("*17-22")),
            item("late").copy(tags = listOf("*22-2")),
        )
        assertEquals(listOf("plain", "monday", "mondayFull", "emptySchedule", "lunch"), ids(MenuRules.visibleEntries(m, monday(12))))
        assertEquals(listOf("plain", "monday", "mondayFull", "emptySchedule", "late"), ids(MenuRules.visibleEntries(m, monday(1))))
        assertEquals(listOf("plain", "monday", "mondayFull", "emptySchedule", "dinner"), ids(MenuRules.visibleEntries(m, monday(21))))
        assertEquals(listOf("plain", "monday", "mondayFull", "emptySchedule"), ids(MenuRules.visibleEntries(m, monday(15))))
    }

    @Test
    fun `cheap items without options or gift are hidden`() {
        val m = menu(
            item("free", price = "0"),
            item("eight", price = "8"),
            item("nine", price = "9"),
            item("withOptions", price = "0").copy(options = listOf("g")),
            item("gift", price = "0").copy(freeGift = JsonObject(emptyMap())),
            item("nullGift", price = "0").copy(freeGift = JsonNull),
            item("noPrice", price = null),
            item("text", price = "ask"),
        )
        assertEquals(listOf("nine", "withOptions", "gift", "noPrice", "text"), ids(MenuRules.visibleEntries(m, monday(12))))
    }

    @Test
    fun `out of stock items go to the end of their category`() {
        val m = menu(
            item("m1").copy(outOfStock = true),
            item("d1", category = "Drinks").copy(outOfStock = true),
            item("m2"),
            item("d2", category = "Drinks"),
            item("m3"),
        )
        assertEquals(listOf("m2", "d2", "m3", "d1", "m1"), ids(MenuRules.visibleEntries(m, monday(12))))
    }

    @Test
    fun `sections follow the category order`() {
        val m = menu(
            item("d1", category = "Drinks"),
            item("m1"),
            item("x1", category = "Unknown"),
            item("n1", category = null),
            categories = listOf(MenuCategory("Mains", en = ""), MenuCategory("Empty"), MenuCategory("Drinks", en = "Drinks EN")),
        )
        val sections = MenuRules.sections(m, m.entries)
        assertEquals(listOf("Mains", "Drinks EN", "Other"), sections.map { it.title })
        assertEquals(listOf(listOf("m1"), listOf("d1"), listOf("x1", "n1")), sections.map { ids(it.entries) })
        assertEquals(listOf("Mains"), MenuRules.sections(m, listOf(m.entries[1])).map { it.title })
    }

    @Test
    fun `prices`() {
        assertEquals(380, MenuRules.basePrice(item("a", price = "380.75")))
        assertNull(MenuRules.basePrice(item("a", price = "ask")))
        assertNull(MenuRules.discountedPrice(item("a", price = null)))
        assertEquals(100, MenuRules.discountedPrice(item("a")))
        assertEquals(100, MenuRules.discountedPrice(item("a").copy(discount = "0")))
        assertEquals(100, MenuRules.discountedPrice(item("a").copy(discount = "")))
        assertEquals(90, MenuRules.discountedPrice(item("a").copy(discount = "10")))
        assertEquals(90, MenuRules.discountedPrice(item("a").copy(discount = "10", discountType = "percent")))
        assertEquals(75, MenuRules.discountedPrice(item("a").copy(discount = "25", discountType = "number")))
        assertEquals(0, MenuRules.discountedPrice(item("a").copy(discount = "250", discountType = "number")))
        // 45 × 0.9 = 40.5. JavaScript Math.round gives 41.
        assertEquals(41, MenuRules.discountedPrice(item("a", price = "45").copy(discount = "10")))
        // 55 × 0.9 = 49.5 gives 50 (not 49 as with half-to-even).
        assertEquals(50, MenuRules.discountedPrice(item("a", price = "55").copy(discount = "10")))
        assertEquals(47, MenuRules.discountedPrice(item("a", price = "49").copy(discount = "5")))
    }

    @Test
    fun `text match`() {
        val e = entry(item("a").copy(name = "Khao Soi", en = LocalizedText(name = "Curry noodles"), description = "Spicy"))
        assertTrue(MenuRules.matches(e, "khao"))
        assertTrue(MenuRules.matches(e, "NOODLE"))
        assertTrue(MenuRules.matches(e, "spic"))
        assertTrue(MenuRules.matches(e, "mains"))
        assertTrue(MenuRules.matches(e, " "))
        assertFalse(MenuRules.matches(e, "pizza"))
    }

    @Test
    fun `all recorded menus`() {
        val menus = Fixtures.menus
        assertEquals(30, menus.size)
        var shown = 0
        for ((name, menu) in menus) {
            for (hour in listOf(3, 12, 20)) {
                val visible = MenuRules.visibleEntries(menu, monday(hour))
                assertTrue(visible.size <= menu.entries.size, name)
                val sections = MenuRules.sections(menu, visible)
                val inSections = sections.flatMap { it.entries }
                assertEquals(visible.size, inSections.size, name)
                assertEquals(visible.toSet(), inSections.toSet(), name)
                assertTrue(sections.all { it.entries.isNotEmpty() && it.title.isNotBlank() }, name)
                assertEquals(sections.size, sections.map { it.title }.toSet().size, "$name: two sections with one title")
                if (hour == 12) shown += visible.size
                for (entry in visible) {
                    assertTrue(entry.item.hidden != true, name)
                    val price = MenuRules.discountedPrice(entry.item)
                    val base = MenuRules.basePrice(entry.item)
                    if (price != null && base != null) assertTrue(price in 0..base, "$name ${entry.item.id}")
                }
            }
        }
        assertTrue(shown > 500, "only $shown items shown")
    }
}
