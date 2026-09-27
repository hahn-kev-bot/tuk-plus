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
    fun `optional single choice can be removed again`() {
        // The owner's bug: "Extra cheese" could not be unselected.
        val g = group("single", required = false)
        val chosen = OptionChoice.tap(g, emptySet(), "a")
        assertEquals(setOf("a"), chosen)
        assertEquals(emptySet(), OptionChoice.tap(g, chosen, "a"))
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
}
