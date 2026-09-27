package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.domain.Fixtures.monday
import app.hahn.tukplus.core.model.PageRow
import app.hahn.tukplus.core.model.PageRowSettings
import app.hahn.tukplus.core.model.PageRowTitle
import app.hahn.tukplus.core.model.PageTile
import app.hahn.tukplus.core.model.TimeSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeRulesTest {
    private fun tile(tag: String, hidden: Boolean = false, rank: Int = 0) =
        PageTile(id = tag, name = tag, tag = tag, rank = rank, hidden = hidden, showName = false, pic = null, localizedPics = emptyMap())

    private fun row(page: String, settings: PageRowSettings?, vararg tiles: PageTile) =
        PageRow(page, PageRowTitle(page = page, title = mapOf("en" to page), settings = settings), tiles.toList())

    private val always: (String) -> Boolean? = { true }

    private fun visible(rows: List<PageRow>, hour: Int = 12, minute: Int = 0, open: (String) -> Boolean? = always, seed: Long = 1) =
        HomeRules.visibleRows(rows, monday(hour, minute), open, seed)

    @Test
    fun `hidden rows and tiles are removed, empty rows are removed, order stays`() {
        val rows = listOf(
            row("A", null, tile("@a"), tile("@b", hidden = true)),
            row("B", PageRowSettings(hidden = true), tile("@c")),
            row("C", PageRowSettings(), tile("@d", hidden = true)),
            PageRow("D", null, listOf(tile("@e"))),
        )
        val result = visible(rows)
        assertEquals(listOf("A", "D"), result.map { it.page })
        assertEquals(listOf("@a"), result[0].tiles.map { it.tag })
    }

    @Test
    fun `time slot`() {
        val rows = listOf(row("A", PageRowSettings(timeSlot = TimeSlot("0700", "2300")), tile("@a")))
        assertTrue(visible(rows, 7, 0).isNotEmpty())
        assertTrue(visible(rows, 22, 59).isNotEmpty())
        assertTrue(visible(rows, 23, 0).isEmpty())
        assertTrue(visible(rows, 6, 59).isEmpty())
    }

    @Test
    fun `time slot values`() {
        assertTrue(HomeRules.inTimeSlot(null, 300))
        assertTrue(HomeRules.inTimeSlot(TimeSlot(null, null), 300))
        assertTrue(HomeRules.inTimeSlot(TimeSlot("700", "0"), 2359))
        assertFalse(HomeRules.inTimeSlot(TimeSlot("700", "0"), 659))
        assertTrue(HomeRules.inTimeSlot(TimeSlot("1700", null), 2359))
        assertFalse(HomeRules.inTimeSlot(TimeSlot(null, "1100"), 1100))
        assertTrue(HomeRules.inTimeSlot(TimeSlot(null, "1100"), 0))
        // A slot past midnight.
        assertTrue(HomeRules.inTimeSlot(TimeSlot("2200", "0200"), 2300))
        assertTrue(HomeRules.inTimeSlot(TimeSlot("2200", "0200"), 100))
        assertFalse(HomeRules.inTimeSlot(TimeSlot("2200", "0200"), 1200))
    }

    @Test
    fun `shop open removes only closed shop tiles`() {
        val rows = listOf(row("A", PageRowSettings(shopOpen = true),
            tile("@open?a=1"), tile("@closed?a=2"), tile("@unknown"), tile("/eat?s=pizza")))
        val status = mapOf("@open" to true, "@closed" to false)
        val result = visible(rows, open = { status[it] })
        assertEquals(listOf("@open?a=1", "@unknown", "/eat?s=pizza"), result.single().tiles.map { it.tag })
        // Without the setting, closed shops stay.
        val plain = listOf(row("A", PageRowSettings(shopOpen = false), tile("@closed")))
        assertEquals(1, visible(plain, open = { false }).single().tiles.size)
    }

    @Test
    fun `randomise shuffles with a stable seed`() {
        val tiles = (1..20).map { tile("@t$it", rank = it) }.toTypedArray()
        val rows = listOf(row("Home_4", PageRowSettings(randomise = true), *tiles))
        val first = visible(rows, seed = 42).single().tiles.map { it.tag }
        assertEquals(first, visible(rows, seed = 42).single().tiles.map { it.tag })
        assertNotEquals(tiles.map { it.tag }, first)
        assertEquals(tiles.map { it.tag }.toSet(), first.toSet())
        assertNotEquals(first, visible(rows, seed = 43).single().tiles.map { it.tag })
    }

    @Test
    fun `recorded pages`() {
        val rows = Fixtures.pageRows
        val noon = visible(rows).map { it.page }
        assertFalse("Home_5" in noon) // The row is hidden.
        assertTrue("Home_2" in noon)
        assertTrue("Eat" in noon)
        // Home_2 has the time slot 0700-2300.
        assertFalse("Home_2" in visible(rows, 23, 30).map { it.page })
        for (row in visible(rows)) assertTrue(row.tiles.none { it.hidden }, row.page)
    }
}
