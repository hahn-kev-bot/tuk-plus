package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.model.LatLon
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/** One field where our value and the web app's value differ. [path] is in `data`, for example "order.vat". */
data class OrderDiff(val path: String, val ours: String?, val web: String?)

/**
 * The result of [OrderCheck.check].
 *
 * - [basketDiffs]: lines of our cart that the web order does not have in the same form, and
 *   the other way round (the user can change the basket in the web app).
 * - [amountDiffs]: amount fields where our price code, run on the web order's own basket,
 *   address, fare distance and payment method, gives another value.
 */
data class OrderCheckResult(
    val basketDiffs: List<OrderDiff>,
    val amountDiffs: List<OrderDiff>,
    /** Why our code cannot price the order, for example a block reason. Null when it can. */
    val notPriced: String?,
) {
    val matches: Boolean get() = basketDiffs.isEmpty() && amountDiffs.isEmpty() && notPriced == null
}

/** The pricing inputs that Tuk plus had when it handed the cart to the web app. */
data class HandoffSnapshot(
    val cart: Cart,
    val shop: ShopSettings,
    val fleets: Fleets?,
    val pickup: LatLon?,
    val menuFreeGift: JsonElement? = null,
)

/**
 * The background check of a web checkout order (PLAN.md §8a): compares the `POST transactions`
 * body that the web app sent with the same order priced by `core:pricing`.
 */
object OrderCheck {

    fun check(snapshot: HandoffSnapshot, body: JsonObject, sentAtEpochMs: Long): OrderCheckResult {
        val data = body["data"] as? JsonObject ?: return OrderCheckResult(emptyList(), emptyList(), "no data in the body")
        val order = data["order"] as? JsonObject
        val webItems = ((order?.get("basket") as? JsonObject)?.get("items") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val basketDiffs = basketDiffs(CartPricing.lines(snapshot.cart, snapshot.shop), webItems)

        val lines = webItems.mapNotNull { line ->
            val item = line["item"] as? JsonObject ?: return@mapNotNull null
            BasketLine(item, Js.number(line["quantity"]).takeIf { !it.isNaN() }?.toInt() ?: 1)
        }
        val address = (data["address"] as? JsonObject)?.let { latLon(it) }
        val fare = data["fare"] as? JsonObject
        val route = fare?.let { f ->
            val distance = Js.number(f["distance"])
            if (distance.isNaN()) null else Route(distance, (f["source"] as? JsonPrimitive)?.content ?: "estimate")
        }
        val quote = Pricing.quote(
            QuoteRequest(
                shop = snapshot.shop,
                lines = lines,
                fulfilment = (data["type"] as? JsonPrimitive)?.content,
                nowEpochMs = sentAtEpochMs,
                fleets = snapshot.fleets,
                pickup = snapshot.pickup,
                address = address,
                route = route,
                paymentMethod = (order?.get("payment_method") as? JsonPrimitive)?.content ?: "cash",
                fulfilmentTime = (order?.get("fulfilment_time") as? JsonPrimitive)?.takeIf { it.isString }?.content,
                menuFreeGift = snapshot.menuFreeGift,
            ),
        )
        val ours = quote.order ?: return OrderCheckResult(basketDiffs, emptyList(), "blocked: " + quote.blocked.joinToString(","))
        val amountDiffs = mutableListOf<OrderDiff>()
        compare("", ours.toJson(), data, amountDiffs)
        return OrderCheckResult(basketDiffs, amountDiffs, null)
    }

    /** Compares each leaf of [ours] with the same path in [web]. Numbers compare by value. */
    private fun compare(prefix: String, ours: JsonObject, web: JsonObject?, out: MutableList<OrderDiff>) {
        for ((key, value) in ours) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val other = web?.get(key)
            if (value is JsonObject) {
                compare(path, value, other as? JsonObject, out)
            } else if (!same(value, other)) {
                out += OrderDiff(path, text(value), text(other))
            }
        }
    }

    private fun same(a: JsonElement?, b: JsonElement?): Boolean {
        if (a == null || a is JsonNull) return b == null || b is JsonNull
        if (b == null || b is JsonNull) return false
        if (a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString) {
            val x = a.doubleOrNull
            val y = b.doubleOrNull
            if (x != null && y != null) return x == y
        }
        return text(a) == text(b)
    }

    private fun text(e: JsonElement?): String? = when (e) {
        null, JsonNull -> null
        is JsonPrimitive -> e.content
        else -> e.toString()
    }

    /** A line is the item id, the chosen options (with quantities) and the note. Quantities add up per key. */
    private fun basketDiffs(ours: List<BasketLine>, web: List<JsonObject>): List<OrderDiff> {
        val a = ours.groupBy({ key(it.item) }, { it.quantity }).mapValues { it.value.sum() }
        val b = web.groupBy({ key(it["item"] as? JsonObject) }, { Js.number(it["quantity"]).takeIf { q -> !q.isNaN() }?.toInt() ?: 1 }).mapValues { it.value.sum() }
        return (a.keys + b.keys).filter { a[it] != b[it] }.map { OrderDiff("basket[$it]", a[it]?.toString(), b[it]?.toString()) }
    }

    private fun key(item: JsonObject?): String {
        if (item == null) return "?"
        val id = (item["id"] as? JsonPrimitive)?.content ?: "?"
        val options = (item["options"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>().mapNotNull { entry ->
            val option = entry["option"] as? JsonObject ?: return@mapNotNull null
            val q = Js.number(option["quantity"]).takeIf { !it.isNaN() && it > 0 }?.toInt() ?: 1
            "${(option["id"] as? JsonPrimitive)?.content}×$q"
        }.sorted()
        val note = (item["comment"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        return listOf(id, options.joinToString("+"), note).joinToString("|")
    }

    private fun latLon(o: JsonObject): LatLon? {
        val lat = Js.number(o["lat"])
        val lon = Js.number(o["lon"])
        return if (lat.isNaN() || lon.isNaN()) null else LatLon(lat, lon)
    }
}
