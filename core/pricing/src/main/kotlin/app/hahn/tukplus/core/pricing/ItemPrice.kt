package app.hahn.tukplus.core.pricing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Item and option prices of the web mixin (app~50b71177, docs/pricing.md §2–§4).
 *
 * The functions take the item in the web basket form: the raw menu item, with `options`
 * as a list of `{menu, option}` (the chosen options). An `option` has `price`,
 * `discounted_price` (optional) and `quantity` (optional).
 */
object ItemPrice {

    /** The value of `weightPrice(item)`: the raw price, or a number for items sold by weight. */
    internal sealed interface Weighted {
        data class Raw(val value: JsonElement?) : Weighted
        data class Num(val value: Double) : Weighted
    }

    internal fun weightPrice(item: JsonObject): Weighted {
        val price = item["price"]
        if (!Js.truthy(item["by_weight"]) || !Js.truthy(item["actual_weight"])) return Weighted.Raw(price)
        val wt = Js.parseInt(item["actual_weight"])
        val byWeight = (item["by_weight"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        return when (byWeight) {
            "per gram" -> Weighted.Num(wt * Js.number(price))
            "per 100 grams" -> Weighted.Num(wt * Js.number(price) / 100)
            "per kg" -> Weighted.Num(wt * Js.number(price) / 1e3)
            else -> Weighted.Raw(price)
        }
    }

    private fun parseInt(w: Weighted): Double = when (w) {
        is Weighted.Raw -> Js.parseInt(w.value)
        is Weighted.Num -> Js.parseInt(w.value)
    }

    /** `weightPrice` as JSON (for tests): the raw value or the number. */
    internal fun weightPriceJson(item: JsonObject): JsonElement? = when (val w = weightPrice(item)) {
        is Weighted.Raw -> w.value
        is Weighted.Num -> Js.json(w.value)
    }

    /** `discountedPrice(item)`: the unit price after the item discount. NaN when the price is not a number. */
    fun discountedPrice(item: JsonObject): Double {
        val e = weightPrice(item)
        val discount = item["discount"]
        if (!Js.truthy(discount)) return parseInt(e)
        var o = parseInt(e) * (1 - Js.number(discount) / 100)
        val type = item["discount_type"] as? JsonPrimitive
        if (type != null && type.isString && type.content == "number") {
            o = parseInt(e) - Js.number(discount)
        }
        return if (o < 0) 0.0 else Js.round(o)
    }

    /** `optionPrice(option)`: `parseInt(discounted_price || price) × (quantity || 1)`. */
    fun optionPrice(option: JsonObject): Double {
        val q = option["quantity"]
        val quantity = if (Js.truthy(q)) Js.number(q) else 1.0
        val dp = option["discounted_price"]
        val price = if (Js.truthy(dp)) dp else option["price"]
        return Js.parseInt(price) * quantity
    }

    /** `discountedTotalPrice(item)`: the unit price of a basket line with its options. */
    fun lineUnit(item: JsonObject): Double {
        var o = 0.0
        (item["options"] as? JsonArray)?.forEach { entry ->
            val option = (entry as? JsonObject)?.get("option")
            if (Js.truthy(option) && option is JsonObject) o += optionPrice(option)
        }
        return discountedPrice(item) + o
    }

    /**
     * `handleVisualDiscount` on one menu item (docs/pricing.md §2.1). Apply it once, to the
     * menu item, before the item goes into the basket.
     */
    fun applyVisualDiscount(item: JsonObject, visualDiscount: Double?): JsonObject {
        if (!Js.truthy(visualDiscount)) return item
        val vd = visualDiscount!!
        val t = 100 * vd
        val d = item["discount"].let { if (Js.truthy(it)) it else JsonPrimitive(0) }
        val discount = Js.round(Js.parseInt(d) + t)
        val price = Js.round(parseFloat(item["price"]) / (1 - vd))
        return JsonObject(item + mapOf("discount" to Js.json(discount), "price" to Js.json(price)))
    }

    /** JavaScript `parseFloat`: the longest leading decimal number. */
    internal fun parseFloat(value: JsonElement?): Double {
        val text = when (value) {
            is JsonPrimitive -> value.content
            else -> return Double.NaN
        }
        val m = Regex("""^[+-]?(Infinity|\d+\.?\d*([eE][+-]?\d+)?|\.\d+([eE][+-]?\d+)?)""").find(text.trimStart()) ?: return Double.NaN
        val s = m.value
        return if (s.endsWith("Infinity")) (if (s.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY) else s.toDouble()
    }
}
