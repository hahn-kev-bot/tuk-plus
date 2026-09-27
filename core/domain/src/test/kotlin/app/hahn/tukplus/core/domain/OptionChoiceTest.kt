package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.OptionGroup
import app.hahn.tukplus.core.model.OptionItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionChoiceTest {
    private val items = listOf(OptionItem("a", "A"), OptionItem("b", "B"), OptionItem("c", "C"), OptionItem("x", "X", outOfStock = true))

    private fun group(select: String?, required: Boolean, constraint: String? = null, n: Int? = null) =
        OptionGroup(name = "g", select = select, required = required, multipleConstraint = constraint, multipleN = n, items = items)

    @Test
    fun `optional group with one option is a check box that can be removed again`() {
        // The owner's bug: "Extra cheese" could not be unselected.
        val g = OptionGroup(name = "cheese", select = "single", required = false, items = listOf(OptionItem("a", "Extra cheese")))
        assertEquals(OptionStyle.CHECKBOX, OptionChoice.style(g))
        val chosen = OptionChoice.tap(g, emptySet(), "a")
        assertEquals(setOf("a"), chosen)
        assertEquals(emptySet(), OptionChoice.tap(g, chosen, "a"))
    }

    @Test
    fun `optional choose one with several options is radio with None`() {
        val g = group("single", required = false)
        assertEquals(OptionStyle.RADIO_WITH_NONE, OptionChoice.style(g))
        var chosen = OptionChoice.tap(g, emptySet(), "a")
        chosen = OptionChoice.tap(g, chosen, "b")
        assertEquals(setOf("b"), chosen) // only one can be active
        assertEquals(setOf("b"), OptionChoice.tap(g, chosen, "b")) // a radio stays on
        assertEquals(emptySet(), OptionChoice.tapNone(g, chosen))
    }

    @Test
    fun `styles`() {
        assertEquals(OptionStyle.RADIO, OptionChoice.style(group("single", required = true)))
        assertEquals(OptionStyle.CHECKBOX, OptionChoice.style(group("multiple", required = false)))
        assertEquals(OptionStyle.CHECKBOX, OptionChoice.style(group("multiple", required = true)))
        // None does nothing in a required group.
        assertEquals(setOf("a"), OptionChoice.tapNone(group("single", required = true), setOf("a")))
    }

    @Test
    fun `required single choice replaces but does not become empty`() {
        val g = group("single", required = true)
        assertEquals(setOf("b"), OptionChoice.tap(g, setOf("a"), "b"))
        assertEquals(setOf("a"), OptionChoice.tap(g, setOf("a"), "a"))
    }

    @Test
    fun `missing select means single choice`() {
        val g = group(null, required = false)
        assertEquals(setOf("b"), OptionChoice.tap(g, setOf("a"), "b"))
    }

    @Test
    fun `multiple choice toggles and respects the limit`() {
        val g = group("multiple", required = false, constraint = "up_to", n = 2)
        var chosen = OptionChoice.tap(g, emptySet(), "a")
        chosen = OptionChoice.tap(g, chosen, "b")
        assertEquals(setOf("a", "b"), chosen)
        assertEquals(chosen, OptionChoice.tap(g, chosen, "c")) // limit reached
        assertFalse(OptionChoice.canAdd(g, chosen, "c"))
        assertEquals(setOf("b"), OptionChoice.tap(g, chosen, "a"))
    }

    @Test
    fun `multiple choice without limit`() {
        val g = group("multiple", required = false, constraint = "none")
        assertEquals(setOf("a", "b", "c"), listOf("a", "b", "c").fold(emptySet<String>()) { s, id -> OptionChoice.tap(g, s, id) })
    }

    @Test
    fun `sold out and unknown options do not change the choice`() {
        val g = group("multiple", required = false)
        assertEquals(setOf("a"), OptionChoice.tap(g, setOf("a"), "x"))
        assertEquals(setOf("a"), OptionChoice.tap(g, setOf("a"), "nope"))
    }

    @Test
    fun `missing choices`() {
        assertTrue(OptionChoice.isMissing(group("single", required = true), emptySet()))
        assertFalse(OptionChoice.isMissing(group("single", required = true), setOf("a")))
        assertFalse(OptionChoice.isMissing(group("single", required = false), emptySet()))
        val exactly2 = group("multiple", required = true, constraint = "exactly", n = 2)
        assertTrue(OptionChoice.isMissing(exactly2, setOf("a")))
        assertFalse(OptionChoice.isMissing(exactly2, setOf("a", "b")))
    }

    @Test
    fun `web rules at add - exactly also for optional groups, up_to, quantities`() {
        val optionalExactly = group("multiple", required = false, constraint = "exactly", n = 2)
        assertTrue(OptionChoice.isMissing(optionalExactly, emptyMap<String, Int>())) // the web app checks it too
        assertFalse(OptionChoice.isMissing(optionalExactly, mapOf("a" to 2))) // one option, quantity 2
        assertTrue(OptionChoice.isMissing(group("multiple", required = false, constraint = "exactly", n = null), mapOf("a" to 1)))
        val upTo = group("multiple", required = false, constraint = "up_to", n = 2)
        assertFalse(OptionChoice.isMissing(upTo, mapOf("a" to 2)))
        assertTrue(OptionChoice.isMissing(upTo, mapOf("a" to 2, "b" to 1)))
    }

    @Test
    fun `visible groups - condition text, order, required first`() {
        fun mg(id: String, g: OptionGroup) = app.hahn.tukplus.core.model.MenuOptionGroup(
            id, g, app.hahn.tukplus.core.model.TukJson.encodeToJsonElement(OptionGroup.serializer(), g) as kotlinx.serialization.json.JsonObject,
        )
        val base = mg("base", OptionGroup(name = "Base", select = "single", items = listOf(OptionItem("r", "Rice"), OptionItem("n", "Noodles"))))
        val soup = mg("soup", OptionGroup(name = "Soup", select = "single", required = true, condition = "noodles", items = listOf(OptionItem("c", "Clear"))))
        val upper = mg("upper", OptionGroup(name = "Upper", select = "single", condition = "Noodles", items = listOf(OptionItem("x", "X"))))
        val empty = mg("empty", OptionGroup(name = "Empty", select = "single"))
        val groups = listOf(base, soup, upper, empty).associateBy { it.blobId }
        val ids = listOf("base", "soup", "upper", "empty", "missing")
        assertEquals(listOf("base"), OptionChoice.visibleGroups(ids, groups, emptyMap()).map { it.blobId })
        val chosen = mapOf("base" to mapOf("n" to 1))
        // "Noodles" with a capital letter never matches: the web app makes the option text lower case only.
        assertEquals(listOf("soup", "base"), OptionChoice.visibleGroups(ids, groups, chosen).map { it.blobId })
        assertEquals(listOf("soup"), OptionChoice.invalidGroups(ids, groups, chosen))
        assertEquals(mapOf("base" to mapOf("r" to 1)), OptionChoice.dropHidden(ids, groups, mapOf("base" to mapOf("r" to 1), "soup" to mapOf("c" to 1))))
    }
}
