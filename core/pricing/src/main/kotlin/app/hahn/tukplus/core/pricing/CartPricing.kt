package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartLine
import app.hahn.tukplus.core.model.LatLon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Makes the price input from the device cart (core:domain [Cart]). */
object CartPricing {

    /**
     * The web basket item of [line]: the menu item (after the shop's visual discount), with
     * `options` = `[{menu, option}]`, `id2` (the line id, for items with option groups) and
     * `comment` (the line note). This is also the item that the order body sends.
     */
    fun webItem(line: CartLine, shop: ShopSettings): JsonObject {
        val item = ItemPrice.applyVisualDiscount(line.item, shop.visualDiscount)
        val options = line.options.mapNotNull { chosen ->
            val group = line.groups[chosen.groupId] ?: return@mapNotNull null
            val raw = (group["items"] as? JsonArray)?.firstOrNull { (it as? JsonObject)?.get("id").let { id -> id is JsonPrimitive && id.content == chosen.optionId } } as? JsonObject
                ?: return@mapNotNull null
            val menu = JsonObject(group.filterKeys { it !in setOf("items", "required", "select") } + ("id" to JsonPrimitive(chosen.groupId)))
            val extra = mutableMapOf<String, JsonElement>("quantity" to JsonPrimitive(chosen.quantity))
            if (shop.propagateDiscounts && discountsOptions(item, group)) {
                extra["discounted_price"] = Js.json(propagatedPrice(raw, item))
            }
            JsonObject(mapOf("menu" to menu, "option" to JsonObject(raw + extra)))
        }
        val hasGroups = (item["options"] as? JsonArray)?.isNotEmpty() == true
        val fields = mutableMapOf<String, JsonElement>("options" to JsonArray(options))
        if (hasGroups) fields["id2"] = JsonPrimitive(line.lineId)
        fields["comment"] = if (line.note.isEmpty()) JsonNull else JsonPrimitive(line.note)
        return JsonObject(item + fields)
    }

    /** The basket lines of [cart]. */
    fun lines(cart: Cart, shop: ShopSettings): List<BasketLine> = cart.lines.map { BasketLine(webItem(it, shop), it.quantity) }

    /** A [QuoteRequest] for [cart]. The order type is `cart.fulfilment`. */
    fun request(
        cart: Cart,
        shop: ShopSettings,
        nowEpochMs: Long,
        fleets: Fleets? = null,
        pickup: LatLon? = null,
        address: LatLon? = null,
        route: Route? = null,
        user: UserDeliveryOptions? = null,
        paymentMethod: String = "cash",
        fulfilmentTime: String? = null,
        menuFreeGift: JsonElement? = null,
    ): QuoteRequest = QuoteRequest(
        shop = shop,
        lines = lines(cart, shop),
        fulfilment = cart.fulfilment,
        nowEpochMs = nowEpochMs,
        fleets = fleets,
        pickup = pickup,
        address = address,
        route = route,
        user = user,
        paymentMethod = paymentMethod,
        fulfilmentTime = fulfilmentTime,
        menuFreeGift = menuFreeGift,
    )

    /**
     * `updateOptionMenuPrices` (docs/pricing.md §3.1): with `propagate_discounts`, the item's
     * percent discount also applies to the options of the groups that the sheet shows when
     * it opens (groups with a `condition` are not shown then).
     */
    private fun discountsOptions(item: JsonObject, group: JsonObject): Boolean {
        val type = (item["discount_type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (Js.truthy(item["discount_type"]) && type != "percent") return false
        if (Js.truthy(group["condition"])) return false
        return (group["items"] as? JsonArray)?.isNotEmpty() == true
    }

    private fun propagatedPrice(option: JsonObject, item: JsonObject): Double {
        val f = 1 - Js.parseInt(item["discount"]) / 100
        return Js.parseInt(option["price"]) * f
    }
}
