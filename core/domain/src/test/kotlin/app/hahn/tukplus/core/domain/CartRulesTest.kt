package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.MenuItem
import app.hahn.tukplus.core.model.MenuOptionGroup
import app.hahn.tukplus.core.model.OptionGroup
import app.hahn.tukplus.core.model.OptionItem
import app.hahn.tukplus.core.model.TukJson
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CartRulesTest {
    private var nextId = 0
    private val newId = { "L${nextId++}" }

    private fun entry(item: MenuItem) = MenuEntry(item, TukJson.encodeToJsonElement(MenuItem.serializer(), item).jsonObject)

    private fun group(id: String, g: OptionGroup) =
        MenuOptionGroup(id, g, TukJson.encodeToJsonElement(OptionGroup.serializer(), g).jsonObject)

    private val size = OptionGroup(name = "Size", select = "single", required = true, items = listOf(OptionItem("s", "Small"), OptionItem("l", "Large", price = "20")))
    private val rice = entry(MenuItem("rice", "Fried rice", price = "60", options = listOf("g1")))
    private val water = entry(MenuItem("water", "Water", price = "15"))

    private fun menu(version: String = "v1", entries: List<MenuEntry> = listOf(rice, water), groups: List<MenuOptionGroup> = listOf(group("g1", size))) =
        Menu("wf1", version, emptyList(), entries, groups.associateBy { it.blobId }, null, emptyList())

    private fun add(cart: Cart?, e: MenuEntry, options: List<CartOption> = emptyList(), qty: Int = 1, note: String = "", now: Long = 1_000L, shop: String = "b1", m: Menu = menu()) =
        CartRules.add(cart, shop, "Shop", m, CartAddition(e, options, qty, note), now, newId)

    private fun added(result: AddResult) = assertIs<AddResult.Added>(result).cart

    @Test
    fun `items without options merge`() {
        var cart = added(add(null, water))
        cart = added(add(cart, water, qty = 2))
        assertEquals(1, cart.lines.size)
        assertEquals(3, cart.lines.single().quantity)
    }

    @Test
    fun `items with options or a note are new lines`() {
        val large = listOf(CartOption("g1", "l"))
        var cart = added(add(null, rice, large))
        cart = added(add(cart, rice, large))
        cart = added(add(cart, water))
        cart = added(add(cart, water, note = "no ice"))
        assertEquals(4, cart.lines.size)
        assertEquals(setOf("g1"), cart.lines.first().groups.keys)
        assertEquals("v1", cart.lines.first().menuVersion)
    }

    @Test
    fun `other shop asks when the cart is new and is replaced when old`() {
        val cart = added(add(null, water, now = 0L))
        val other = menu().copy(workflowId = "wf2")
        assertIs<AddResult.OtherShop>(add(cart, water, shop = "b2", m = other, now = CartRules.REPLACE_AFTER_MS))
        val replaced = added(add(cart, water, shop = "b2", m = other, now = CartRules.REPLACE_AFTER_MS + 1))
        assertEquals("b2", replaced.businessId)
        assertEquals(1, replaced.lines.size)
        val fresh = CartRules.startNew("b2", "Other", other, CartAddition(water, emptyList(), 1), 5L, newId)
        assertEquals("wf2", fresh.workflowId)
        assertEquals(5L, fresh.createdAt)
    }

    @Test
    fun `quantity changes and removal`() {
        var cart = added(add(null, water))
        val id = cart.lines.single().lineId
        cart = CartRules.setQuantity(cart, id, 500, 2L)
        assertEquals(CartRules.MAX_QUANTITY, cart.lines.single().quantity)
        cart = CartRules.setQuantity(cart, id, 0, 3L)
        assertTrue(cart.isEmpty)
    }

    @Test
    fun `reconcile finds removed, sold out, option and price changes`() {
        var cart = added(add(null, rice, listOf(CartOption("g1", "l"))))
        cart = added(add(cart, water))
        assertEquals(80, LinePrice.previewUnit(cart.lines[0]))

        val newSize = size.copy(items = listOf(OptionItem("s", "Small"), OptionItem("l", "Large", price = "25")))
        val newMenu = menu("v2", entries = listOf(rice), groups = listOf(group("g1", newSize)))
        val result = CartRules.reconcile(cart, newMenu)
        assertEquals(1, result.cart.lines.size)
        assertEquals("v2", result.cart.lines.single().menuVersion)
        assertEquals(
            listOf(LineChange.Removed("L1", "Water"), LineChange.PriceChanged("L0", "Fried rice", 80, 85)).toSet(),
            result.changes.toSet(),
        )

        val soldOut = menu("v3", entries = listOf(entry(rice.item.copy(outOfStock = true))), groups = listOf(group("g1", size.copy(items = listOf(OptionItem("s", "Small"))))))
        val blocked = CartRules.reconcile(result.cart, soldOut)
        assertEquals(setOf("L0"), blocked.blockedLineIds)
        assertTrue(blocked.changes.any { it is LineChange.OptionUnavailable && it.option == "Large" })
    }

    @Test
    fun `cart survives a JSON round trip`() {
        val cart = added(add(null, rice, listOf(CartOption("g1", "l", 2)), note = "spicy"))
        val json = CartStoreJson.encodeToString(Cart.serializer(), cart)
        assertEquals(cart, CartStoreJson.decodeFromString(Cart.serializer(), json))
        assertEquals(listOf("2× Large"), LinePrice.optionNames(cart.lines.single()))
    }

    @Test
    fun `options from the item sheet keep the menu order`() {
        val g = group("g1", size)
        assertEquals(listOf(CartOption("g1", "l")), CartRules.options(listOf(g), mapOf("g1" to setOf("l", "unknown"))))
        assertEquals(emptyList(), CartRules.options(listOf(g), emptyMap<String, Set<String>>()))
    }
}
