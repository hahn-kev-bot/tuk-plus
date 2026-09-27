package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.WorkflowData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compares the port with the web app's own price code. The expected values come from
 * tools/price-parity (docs/pricing.md §18). One failure message lists every differing field.
 */
class ParityTest {

    private val root: JsonObject = run {
        val text = javaClass.classLoader.getResource("parity/cases.json")?.readText() ?: fail("parity/cases.json is missing")
        TukJson.parseToJsonElement(text).jsonObject
    }
    private val cases: List<JsonObject> = root["cases"]!!.jsonArray.map { it.jsonObject }
    private val fleets: Map<String, Fleet> = root["header"]!!.jsonObject["fleets"]!!.jsonObject.mapValues { (_, v) -> fleet(v.jsonObject) }

    private fun kind(k: String) = cases.filter { it.str("kind") == k }

    // ------------------------------------------------------------ checkout

    @Test
    fun `checkout cases match the web app`() {
        val all = kind("checkout")
        assertTrue(all.size > 700, "too few checkout cases: ${all.size}")
        val diffs = Diffs()
        for (c in all) checkCheckout(c, diffs)
        diffs.assertNone("checkout", all.size)
    }

    private fun request(input: JsonObject, clock: JsonObject): QuoteRequest {
        val data = TukJson.decodeFromJsonElement(WorkflowData.serializer(), input["workflow"]!!.jsonObject["data"]!!)
        val business = input["business"]!!.jsonObject
        val dw = input["delivery_workflows"]
        val address = input["address"] as? JsonObject
        val route = input["route"] as? JsonObject
        val user = (input["user"] as? JsonObject)?.get("delivery_options") as? JsonObject
        return QuoteRequest(
            shop = ShopSettings.from(data),
            lines = input["basket"]!!.jsonObject["items"]!!.jsonArray.map {
                val o = it.jsonObject
                BasketLine(o["item"]!!.jsonObject, o["quantity"]!!.jsonPrimitive.content.toInt())
            },
            fulfilment = input.str("fulfilment_type"),
            nowEpochMs = clock["epoch_ms"]!!.jsonPrimitive.content.toLong(),
            fleets = (dw as? JsonObject)?.let { Fleets(it.str("express")?.let(fleets::getValue), it.str("fallback")?.let(fleets::getValue)) },
            pickup = LatLon(business.num("lat")!!, business.num("lon")!!),
            address = address?.let { LatLon(it.num("lat")!!, it.num("lon")!!) },
            route = route?.let { Route(it.num("distance")!!, it.str("source")!!) },
            user = user?.let { UserDeliveryOptions(it.num("remit_amount"), it.num("extra_distance")) },
            paymentMethod = input.str("payment_method") ?: "cash",
            fulfilmentTime = input.str("fulfilment_time"),
            menuFreeGift = (input["menu_items"] as? JsonArray)?.let { items -> menuFreeGift(items.map { it.jsonObject }) },
        )
    }

    private fun checkCheckout(c: JsonObject, d: Diffs) {
        val id = c.str("id")!!
        val input = c["input"]!!.jsonObject
        val e = c["expected"]!!.jsonObject
        val q = try {
            Pricing.quote(request(input, c["clock"]!!.jsonObject))
        } catch (t: Throwable) {
            d.add(id, "exception", "no exception", t.toString())
            return
        }
        val w = q.web
        d.num(id, "line_totals", e["line_totals"], w.lineUnits)
        d.num(id, "basket_value", e["basket_value"], w.basketValue)
        d.num(id, "vatable_basket_value", e["vatable_basket_value"], w.vatableBasketValue)
        d.num(id, "item_count", e["item_count"], q.itemCount.toDouble())
        d.num(id, "vat", e["vat"], w.vat)
        val dc = when (q.distanceCheck) {
            null -> null
            DistanceCheck.OK -> "ok"
            DistanceCheck.TOO_SHORT -> "too_short"
            DistanceCheck.TOO_FAR -> "too_far"
        }
        d.eq(id, "distance_check", e.str("distance_check"), dc)
        d.num(id, "max_delivery_distance", e["max_delivery_distance"], w.maxDeliveryDistance)
        d.eq(id, "is_peak_hour", e.bool("is_peak_hour"), w.isPeakHour)
        if (dc == "too_short" || dc == "too_far") {
            d.eq(id, "blocked (distance)", true, q.blocked.any { it == BlockReason.DISTANCE_TOO_FAR || it == BlockReason.DISTANCE_TOO_SHORT })
            d.eq(id, "order (distance)", null, q.order)
            return
        }
        d.num(id, "fulfilment_discount_percent", e["fulfilment_discount_percent"], w.fulfilmentDiscountPercent)
        d.num(id, "fulfilment_discount_amount", e["fulfilment_discount_amount"], w.fulfilmentDiscountAmount)
        d.json(id, "fare", e["fare"], q.fare?.toJson())
        d.num(id, "delivery_subsidy_percent", e["delivery_subsidy_percent"], w.deliverySubsidyPercent)
        d.num(id, "delivery_subsidy", e["delivery_subsidy"], w.deliverySubsidy)
        d.num(id, "actual_delivery_subsidy", e["actual_delivery_subsidy"], w.actualDeliverySubsidy)
        d.num(id, "remit_percent", e["remit_percent"], w.remitPercent)
        d.num(id, "remit_amount", e["remit_amount"], w.remitAmount)
        d.num(id, "actual_remit_amount", e["actual_remit_amount"], w.actualRemitAmount)
        d.num(id, "special_remit", e["special_remit"], w.specialRemit)
        d.num(id, "special_discount", e["special_discount"], w.specialDiscount)
        d.num(id, "billing_percent", e["billing_percent"], w.billingPercent)
        d.eq(id, "billing_amount", e.str("billing_amount"), w.billingAmount)
        d.eq(id, "free_delivery", Js.truthy(e["free_delivery"]), w.freeDelivery)
        d.num(id, "fixed_free_delivery_over", e["fixed_free_delivery_over"], w.fixedFreeDeliveryOver)
        d.num(id, "dynamic_free_delivery_over", e["dynamic_free_delivery_over"], w.dynamicFreeDeliveryOver)
        d.num(id, "free_delivery_over", e["free_delivery_over"], w.freeDeliveryOver)
        d.num(id, "free_delivery_remainder", e["free_delivery_remainder"], w.freeDeliveryRemainder)
        d.eq(id, "inside_free_delivery_polygon", e.bool("inside_free_delivery_polygon"), w.insideFreeDeliveryPolygon)
        d.eq(id, "is_legacy_free_delivery", e.bool("is_legacy_free_delivery"), w.isLegacyFreeDelivery)
        d.eq(id, "is_free_delivery", e.bool("is_free_delivery"), w.isFreeDelivery)
        d.num(id, "actual_delivery_fee", e["actual_delivery_fee"], w.actualDeliveryFee)
        d.num(id, "delivery_fee", e["delivery_fee"], w.deliveryFee)
        d.num(id, "total_value", e["total_value"], w.totalValue)
        d.eq(id, "cash_allowed", e.bool("cash_allowed"), q.cashAllowed)
        val blockedBy = when (e.str("blocked_by")) {
            null -> null
            "promptMinOrder" -> BlockReason.MIN_ORDER
            "promptFreeGiftOverRemoval" -> BlockReason.FREE_GIFT_BELOW_THRESHOLD
            "promptFulfilmentType" -> BlockReason.NO_FULFILMENT
            else -> BlockReason.EMPTY_BASKET
        }
        d.eq(id, "blocked_by", blockedBy, q.blocked.firstOrNull())

        val order = e["order"] as? JsonObject
        if (order == null) {
            d.eq(id, "order", null, q.order)
            return
        }
        val ours = q.order?.toJson()
        if (ours == null) {
            d.add(id, "order", "an order", "null (blocked: ${q.blocked})")
            return
        }
        d.eq(id, "order.type", order.str("type"), ours.str("type"))
        d.json(id, "order.order", order["order"], ours["order"])
        d.json(id, "order.settings", order["settings"], ours["settings"])
        // The generator does not record `reversed`.
        d.json(id, "order.delivery", order["delivery"], (ours["delivery"] as? JsonObject)?.let { JsonObject(it - "reversed") })
        d.eq(id, "order.billing", order.str("billing"), ours.str("billing"))
        if (order.bool("fare_equals_expected_fare") == true) d.json(id, "order.fare", e["fare"], ours["fare"])
        if (order["fare_equals_expected_fare"] == null || order["fare_equals_expected_fare"] is JsonNull) d.eq(id, "order.fare (none)", null, ours["fare"])
        // Port decision 2: our total is the order page total.
        d.num(id, "order_page_total", e["order_page_total"], q.total?.toDouble())
    }

    // ------------------------------------------------------------ small functions

    @Test
    fun `package codes match the web app`() {
        val all = kind("package")
        val d = Diffs()
        for (c in all) {
            val id = c.str("id")!!
            val code = c["input"]!!.jsonObject.str("fruit")
            val e = c["expected"]!!.jsonObject
            d.eq(id, "has_free_delivery", e.bool("has_free_delivery"), PackageCode.hasFreeDelivery(code))
            d.num(id, "delivery_subsidy", e["delivery_subsidy"], PackageCode.deliverySubsidy(code))
            d.num(id, "billing", e["billing"], PackageCode.billing(code))
            d.num(id, "remit", e["remit"], PackageCode.remit(code))
        }
        d.assertNone("package", all.size)
    }

    @Test
    fun `item prices match the web app`() {
        val all = kind("item_price")
        val d = Diffs()
        for (c in all) {
            val id = c.str("id")!!
            val input = c["input"]!!.jsonObject
            val e = c["expected"]!!.jsonObject
            var item = input["item"]!!.jsonObject
            if (input.containsKey("visual_discount")) {
                item = ItemPrice.applyVisualDiscount(item, input.num("visual_discount"))
                val after = e["item_after_visual_discount"]!!.jsonObject
                d.json(id, "item_after_visual_discount", after, JsonObject(mapOf("price" to item["price"]!!, "discount" to item["discount"]!!)))
            }
            d.json(id, "weight_price", e["weight_price"], ItemPrice.weightPriceJson(item))
            d.num(id, "discounted_price", e["discounted_price"], ItemPrice.discountedPrice(item))
            val options = (item["options"] as? JsonArray).orEmpty().map { o ->
                val opt = (o as? JsonObject)?.get("option") as? JsonObject
                opt?.let { ItemPrice.optionPrice(it) }
            }
            d.num(id, "option_prices", e["option_prices"], options)
            d.num(id, "discounted_total_price", e["discounted_total_price"], ItemPrice.lineUnit(item))
        }
        d.assertNone("item_price", all.size)
    }

    @Test
    fun `straight-line distances match the web app`() {
        val all = kind("trip_distance")
        val d = Diffs()
        for (c in all) {
            val id = c.str("id")!!
            val i = c["input"]!!.jsonObject
            val e = c["expected"]!!.jsonObject
            val a = LatLon(i.num("lat1")!!, i.num("lon1")!!)
            val b = LatLon(i.num("lat2")!!, i.num("lon2")!!)
            d.num(id, "straight_m", e["straight_m"], Geo.straightMetres(a, b))
            d.num(id, "trip_m", e["trip_m"], Geo.tripMetres(a, b, i.num("extra_distance") ?: 0.0))
        }
        d.assertNone("trip_distance", all.size)
    }

    // ------------------------------------------------------------ helpers

    private fun fleet(o: JsonObject): Fleet {
        val d = o["data"]?.jsonObject ?: return Fleet()
        return Fleet(
            pricingArray = (d["pricing_array"] as? JsonArray)?.map { it.jsonPrimitive.doubleOrNull ?: Double.NaN },
            surge = d.num("surge"),
            remitAmount = d.num("remit_amount"),
            peakHourMaxDistance = d.num("peak_hour_max_distance"),
        )
    }
}

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull
internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/** Collects differences and fails with one readable message. */
internal class Diffs {
    private val list = mutableListOf<String>()
    private val cases = mutableSetOf<String>()

    fun add(id: String, field: String, expected: Any?, actual: Any?) {
        list += "$id $field: expected $expected, got $actual"
        cases += id
    }

    fun eq(id: String, field: String, expected: Any?, actual: Any?) {
        if (expected != actual) add(id, field, expected, actual)
    }

    /** A JSON number (or null = JavaScript null/NaN) against a Double (NaN or null = null). */
    fun num(id: String, field: String, expected: JsonElement?, actual: Double?) {
        val e = (expected as? JsonPrimitive)?.takeIf { it !is JsonNull }?.doubleOrNull
        val a = actual?.takeIf { !it.isNaN() }
        val same = if (e == null || a == null) e == null && a == null else e == a
        if (!same) add(id, field, e, a)
    }

    fun num(id: String, field: String, expected: JsonElement?, actual: List<Double?>) {
        val e = (expected as? JsonArray) ?: return add(id, field, expected, actual)
        if (e.size != actual.size) return add(id, field, e, actual)
        e.forEachIndexed { i, x -> num(id, "$field[$i]", x, actual[i]) }
    }

    /** JSON values; numbers are compared by value, missing keys must be missing. */
    fun json(id: String, field: String, expected: JsonElement?, actual: JsonElement?) {
        if (!sameJson(expected, actual)) add(id, field, expected, actual)
    }

    private fun sameJson(a: JsonElement?, b: JsonElement?): Boolean {
        val an = a == null || a is JsonNull
        val bn = b == null || b is JsonNull
        if (an || bn) return an && bn
        return when {
            a is JsonObject && b is JsonObject -> a.keys == b.keys && a.keys.all { sameJson(a[it], b[it]) }
            a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { sameJson(a[it], b[it]) }
            a is JsonPrimitive && b is JsonPrimitive -> when {
                a.isString || b.isString -> a.isString == b.isString && a.content == b.content
                a.doubleOrNull != null && b.doubleOrNull != null -> a.doubleOrNull == b.doubleOrNull
                else -> a.content == b.content
            }
            else -> false
        }
    }

    fun assertNone(kind: String, total: Int) {
        if (list.isEmpty()) return
        fail("$kind: ${cases.size} of $total cases differ (${list.size} fields):\n" + list.take(80).joinToString("\n"))
    }
}
