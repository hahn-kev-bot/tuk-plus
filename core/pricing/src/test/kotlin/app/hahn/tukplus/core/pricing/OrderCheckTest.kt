package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartLine
import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.WorkflowData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The background order check must say "match" for orders that the web app's own code made.
 * The parity cases give such orders: the expected `order` values plus the case's basket,
 * address and fare make the body that the web app would send.
 */
class OrderCheckTest {
    private val root = TukJson.parseToJsonElement(javaClass.classLoader.getResource("parity/cases.json")!!.readText()).jsonObject
    private val fleets = root["header"]!!.jsonObject["fleets"]!!.jsonObject.mapValues { (_, v) ->
        val d = v.jsonObject["data"]!!.jsonObject
        Fleet(
            pricingArray = d["pricing_array"]?.jsonArray?.map { Js.number(it) },
            surge = (d["surge"] as? JsonPrimitive)?.let { Js.number(it) },
            remitAmount = (d["remit_amount"] as? JsonPrimitive)?.let { Js.number(it) },
            peakHourMaxDistance = (d["peak_hour_max_distance"] as? JsonPrimitive)?.let { Js.number(it) },
        )
    }

    private fun body(input: JsonObject, expected: JsonObject): JsonObject {
        val order = expected["order"]!!.jsonObject
        val data = order.toMutableMap()
        val values = order["order"]!!.jsonObject.toMutableMap()
        values["basket"] = buildJsonObject { put("items", input["basket"]!!.jsonObject["items"]!!) }
        data["order"] = JsonObject(values)
        (input["address"] as? JsonObject)?.let { data["address"] = it }
        // The parity fixtures leave out `delivery.reversed`; the web code sends it (docs/pricing.md §14).
        val reversed = ((input["workflow"]!!.jsonObject["data"] as? JsonObject)?.get("delivery_options") as? JsonObject)?.get("reversed")
        val delivery = data["delivery"] as? JsonObject
        if (delivery != null && reversed is JsonPrimitive && reversed.content == "true") data["delivery"] = JsonObject(delivery + ("reversed" to JsonPrimitive(true)))
        // The parity cases keep the fare next to the order; the web body has it in `data.fare`.
        (expected["fare"] as? JsonObject)?.takeIf { order["type"]?.jsonPrimitive?.content == "delivery" }?.let { data["fare"] = it }
        return buildJsonObject { put("data", JsonObject(data)) }
    }

    private fun fleetOf(key: kotlinx.serialization.json.JsonElement?): Fleet? =
        (key as? JsonPrimitive)?.takeIf { it.isString }?.content?.let(fleets::getValue)

    private fun snapshot(input: JsonObject): HandoffSnapshot {
        val wf = TukJson.decodeFromJsonElement(WorkflowData.serializer(), input["workflow"]!!.jsonObject["data"]!!)
        val business = input["business"]!!.jsonObject
        val dw = input["delivery_workflows"] as? JsonObject
        val items = input["basket"]!!.jsonObject["items"]!!.jsonArray.mapIndexed { i, line ->
            val item = line.jsonObject["item"]!!.jsonObject
            // A cart line whose web form is this item: no options, so the item is used as is.
            CartLine("L$i", item["id"]!!.jsonPrimitive.content, line.jsonObject["quantity"]!!.jsonPrimitive.content.toInt(), item = JsonObject(item - "options" - "id2" - "comment"))
        }
        return HandoffSnapshot(
            cart = Cart("b", "wf", createdAt = 0L, lines = items),
            shop = ShopSettings.from(wf),
            fleets = dw?.let { Fleets(fleetOf(it["express"]), fleetOf(it["fallback"])) },
            pickup = LatLon(Js.number(business["lat"]), Js.number(business["lon"])),
        )
    }

    @Test
    fun `web orders made by the web code match`() {
        val cases = root["cases"]!!.jsonArray.map { it.jsonObject }.filter {
            it["kind"]!!.jsonPrimitive.content == "checkout" &&
                it["expected"]!!.jsonObject["order"] is JsonObject &&
                it["input"]!!.jsonObject["user"] !is JsonObject && // the check has no user remit (no login yet)
                it["input"]!!.jsonObject["menu_items"] == null
        }
        assertTrue(cases.size > 300, "too few cases: ${cases.size}")
        val failures = cases.mapNotNull { c ->
            val input = c["input"]!!.jsonObject
            val now = c["clock"]!!.jsonObject["epoch_ms"]!!.jsonPrimitive.content.toLong()
            val result = OrderCheck.check(snapshot(input), body(input, c["expected"]!!.jsonObject), now)
            val diffs = result.amountDiffs + listOfNotNull(result.notPriced?.let { OrderDiff("not_priced", it, null) })
            if (diffs.isEmpty()) null else "${c["id"]}: $diffs"
        }
        assertTrue(failures.isEmpty(), "${failures.size} of ${cases.size} cases differ:\n" + failures.take(20).joinToString("\n"))
    }

    @Test
    fun `a changed amount and a changed basket are reported`() {
        val c = root["cases"]!!.jsonArray.map { it.jsonObject }.first {
            it["kind"]!!.jsonPrimitive.content == "checkout" && it["expected"]!!.jsonObject["order"] is JsonObject && it["input"]!!.jsonObject["user"] !is JsonObject && it["input"]!!.jsonObject["menu_items"] == null
        }
        val input = c["input"]!!.jsonObject
        val now = c["clock"]!!.jsonObject["epoch_ms"]!!.jsonPrimitive.content.toLong()
        val good = body(input, c["expected"]!!.jsonObject)
        val data = good["data"]!!.jsonObject
        val order = data["order"]!!.jsonObject
        val vat = Js.number(order["vat"])
        val bad = buildJsonObject {
            put("data", JsonObject(data + ("order" to JsonObject(order + ("vat" to JsonPrimitive(vat + 1))))))
        }
        val snapshot = snapshot(input)
        val result = OrderCheck.check(snapshot.copy(cart = snapshot.cart.copy(lines = snapshot.cart.lines.drop(1))), bad, now)
        assertEquals(listOf("order.vat"), result.amountDiffs.map { it.path })
        assertTrue(result.basketDiffs.isNotEmpty())
        assertTrue(!result.matches)
    }
}
