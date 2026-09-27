package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.domain.AddResult
import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartAddition
import app.hahn.tukplus.core.domain.CartLine
import app.hahn.tukplus.core.domain.CartOption
import app.hahn.tukplus.core.domain.CartRules
import app.hahn.tukplus.core.domain.OptionChoice
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.MenuItem
import app.hahn.tukplus.core.model.MenuOptionGroup
import app.hahn.tukplus.core.model.OptionGroup
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.WorkflowData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The web option sheet and basket rules (kinds `option_sheet` and `basket` of parity/cases.json)
 * against core:domain [OptionChoice] and [CartRules]. The web taps are UI; the test uses the web's
 * final choice and checks the rules that decide a valid order (docs/pricing.md §15–§16).
 */
class CartRulesParityTest {
    private val cases: List<JsonObject> = run {
        val text = javaClass.classLoader.getResource("parity/cases.json")?.readText() ?: fail("parity/cases.json is missing")
        TukJson.parseToJsonElement(text).jsonObject["cases"]!!.jsonArray.map { it.jsonObject }
    }

    @Test
    fun `option sheet rules match the web app`() {
        val all = cases.filter { it.str("kind") == "option_sheet" }
        assertTrue(all.size > 100)
        val d = Diffs()
        for (c in all) {
            val id = c.str("id")!!
            val input = c["input"]!!.jsonObject
            val e = c["expected"]!!.jsonObject
            val rawItem = input["menu_items"]!!.jsonArray.map { it.jsonObject }.first { it.str("id") == input.str("item_id") }
            val item = TukJson.decodeFromJsonElement(MenuItem.serializer(), rawItem)
            val groups = input["options_menus"]!!.jsonArray.map { it.jsonObject }.associate { blob ->
                val data = blob["data"]!!.jsonObject
                blob.str("id")!! to MenuOptionGroup(blob.str("id")!!, TukJson.decodeFromJsonElement(OptionGroup.serializer(), data), data)
            }
            val chosen = mutableMapOf<String, MutableMap<String, Int>>()
            e["selected"]!!.jsonArray.forEach {
                val s = it.jsonObject
                chosen.getOrPut(s.str("menu_id")!!) { mutableMapOf() }[s.str("option_id")!!] = s["quantity"]!!.jsonPrimitive.content.toInt()
            }
            val ids = item.options.orEmpty()

            d.eq(id, "shown_menus", e["shown_menus"]!!.jsonArray.map { it.jsonPrimitive.content }, OptionChoice.visibleGroups(ids, groups, chosen).map { it.blobId })

            val qty = input["qty"]?.jsonPrimitive?.content?.toInt() ?: 0
            val cart = if (qty > 0) Cart("b", "w", createdAt = 0, lines = listOf(CartLine("l0", item.id, qty, item = rawItem))) else null
            val limit = CartRules.addLimit(cart, item)
            val limitOk = limit == null || limit > 0
            val invalid = OptionChoice.invalidGroups(ids, groups, chosen)
            val reason = e.str("blocked_reason")
            val limitBlocked = reason != null && (reason.startsWith("menu.you-have-max-allowed") || reason.contains("Free Gift"))
            d.eq(id, "limit blocks", limitBlocked, !limitOk)
            if (!limitBlocked) d.eq(id, "invalid_menus", e["invalid_menus"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), invalid.toSet())
            d.eq(id, "submitted", e["submitted"]!!.jsonPrimitive.content.toBoolean(), limitOk && invalid.isEmpty())

            // Sheet total: the same price code as the basket (through CartPricing).
            val data = TukJson.decodeFromJsonElement(WorkflowData.serializer(), input["workflow"]!!.jsonObject["data"]!!)
            val options = e["selected"]!!.jsonArray.map {
                val s = it.jsonObject
                CartOption(s.str("menu_id")!!, s.str("option_id")!!, s["quantity"]!!.jsonPrimitive.content.toInt())
            }
            val line = CartLine("l1", item.id, 1, options = options, item = rawItem, groups = groups.mapValues { it.value.raw })
            d.num(id, "total_price", e["total_price"], ItemPrice.lineUnit(CartPricing.webItem(line, ShopSettings.from(data))))
        }
        d.assertNone("option_sheet", all.size)
    }

    @Test
    fun `basket rules match the web app`() {
        val all = cases.filter { it.str("kind") == "basket" }
        val d = Diffs()
        for (c in all) {
            val id = c.str("id")!!
            val input = c["input"]!!.jsonObject
            val e = c["expected"]!!.jsonObject
            val now = c["clock"]!!.jsonObject["epoch_ms"]!!.jsonPrimitive.content.toLong()
            val businessId = input["business"]!!.jsonObject.str("id")!!
            val workflowId = input["workflow"]!!.jsonObject.str("id")!!
            val stored = input["stored_basket"] as? JsonObject
            var cart: Cart? = stored?.let { s ->
                val sid = s.str("shop_id")!!
                Cart(
                    businessId = sid,
                    workflowId = if (sid == businessId) workflowId else "other",
                    createdAt = Instant.parse(s.str("created_at")!!).toEpochMilli(),
                    lines = s["items"]!!.jsonArray.mapIndexed { i, l ->
                        val o = l.jsonObject
                        val item = o["item"]!!.jsonObject
                        CartLine("s$i", item.str("id")!!, o["quantity"]!!.jsonPrimitive.content.toInt(), item = item)
                    },
                )
            }
            val states = e["states"]!!.jsonArray.map { it.jsonObject }
            val steps = input["steps"]!!.jsonArray.map { it.jsonObject }
            var n = 0
            var comments = false
            for ((i, step) in steps.withIndex()) {
                val state = states[i]
                when (step.str("op")) {
                    "init" -> Unit // our cart decides on the first add
                    "add" -> {
                        val item = step["item"]!!.jsonObject
                        if (item.containsKey("comment") && item.str("comment") != null) comments = true
                        val menuItem = TukJson.decodeFromJsonElement(MenuItem.serializer(), JsonObject(item - "options" - "id2" - "comment"))
                        val options = (item["options"]?.jsonArray.orEmpty()).map { o ->
                            val oo = o.jsonObject
                            CartOption(oo["menu"]!!.jsonObject.str("id")!!, oo["option"]!!.jsonObject.str("id")!!)
                        }
                        val entry = MenuEntry(menuItem, JsonObject(item - "options" - "id2" - "comment"))
                        val menu = Menu(workflowId, "v1", emptyList(), listOf(entry), emptyMap(), null, emptyList())
                        val addition = CartAddition(entry, options, 1, item.str("comment").orEmpty())
                        val result = CartRules.add(cart, businessId, "Shop", menu, addition, now) { "n${n++}" }
                        val prompt = result is AddResult.OtherShop
                        d.eq(id, "step $i other_shop_prompt", state["other_shop_prompt"]?.jsonPrimitive?.content?.toBoolean(), prompt)
                        if (result is AddResult.Added) cart = result.cart
                    }
                    else -> continue // removal from the menu: our cart screen removes lines by line id
                }
                if (step.str("op") == "add") {
                    // Same shop and the same quantity of each item. Line count only without notes (ours: a note makes a new line).
                    d.eq(id, "step $i shop_id", state.str("shop_id"), cart?.businessId)
                    val webQty = state["lines"]!!.jsonArray.map { it.jsonObject }.groupBy { it.str("id") }.mapValues { (_, l) -> l.sumOf { it["quantity"]!!.jsonPrimitive.content.toInt() } }
                    val ourQty = cart?.lines.orEmpty().groupBy { it.itemId }.mapValues { (_, l) -> l.sumOf { it.quantity } }
                    d.eq(id, "step $i quantities", webQty, ourQty)
                    if (!comments) d.eq(id, "step $i line count", state["lines"]!!.jsonArray.size, cart?.lines?.size ?: 0)
                }
            }
            // The 60-minute rule for a stored basket: kept or replaced when the user adds from this shop.
            if (stored != null && steps.size == 1) {
                val kept = states[0]["kept_stored_basket"]?.jsonPrimitive?.content?.toBoolean() == true
                val otherShop = stored.str("shop_id") != businessId
                val hasItems = stored["items"]!!.jsonArray.isNotEmpty()
                val web = when {
                    !kept -> "new"
                    otherShop && hasItems -> "prompt"
                    otherShop -> "new"
                    else -> "kept"
                }
                val entry = MenuEntry(MenuItem(id = "probe", name = "Probe", price = "10"), JsonObject(mapOf("id" to JsonPrimitive("probe"), "price" to JsonPrimitive("10"))))
                val menu = Menu(workflowId, "v1", emptyList(), listOf(entry), emptyMap(), null, emptyList())
                val ours = when (val r = CartRules.add(cart, businessId, "Shop", menu, CartAddition(entry, emptyList(), 1), now) { "p" }) {
                    is AddResult.OtherShop -> "prompt"
                    is AddResult.Added -> if (r.cart.lines.size > 1) "kept" else "new"
                }
                d.eq(id, "60-minute rule", web, ours)
            }
        }
        d.assertNone("basket", all.size)
    }
}
