package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartLine
import app.hahn.tukplus.core.domain.CartOption
import app.hahn.tukplus.core.model.LatLon
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime
import app.hahn.tukplus.core.domain.ChiangMaiTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PricingTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
    private fun at(hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, 9, 27, hour, minute, 0, 0, ChiangMaiTime.ZONE).toInstant().toEpochMilli()

    private val cmExpress = Fleet(
        pricingArray = listOf(30, 30, 40, 40, 46, 52, 58, 64, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 225, 240, 255, 275, 300).map { it.toDouble() },
        surge = 0.0,
        remitAmount = 10.0,
        peakHourMaxDistance = 27.0,
    )
    private val shop = ShopSettings(fruit = "r_20_10", express = "Chiang Mai Express Delivery", shopRemit = 3.0, deliveryType = "delayed", delayDuration = 15.0)
    private val lines = listOf(
        BasketLine(obj("""{"id":"i1","name":"A","price":"120","options":[]}"""), 2),
        BasketLine(obj("""{"id":"i2","name":"B","price":"140","discount":10,"id2":"x","options":[{"menu":{"id":"m"},"option":{"id":"o","price":"20","quantity":2}}]}"""), 1),
    )

    @Test
    fun `item price rules like the web app`() {
        assertEquals(12.0, ItemPrice.discountedPrice(obj("""{"price":"12.9"}"""))) // parseInt cuts
        assertEquals(41.0, ItemPrice.discountedPrice(obj("""{"price":"45","discount":10}"""))) // 40.5 rounds up
        assertEquals(1.0, ItemPrice.discountedPrice(obj("""{"price":"15","discount":90}"""))) // 1.4999999999999996
        assertEquals(0.0, ItemPrice.discountedPrice(obj("""{"price":"50","discount":60,"discount_type":"number"}""")))
        assertTrue(ItemPrice.discountedPrice(obj("""{"price":""}""")).isNaN())
        assertEquals(30.0, ItemPrice.optionPrice(obj("""{"price":"15","quantity":2}""")))
        assertEquals(10.0, ItemPrice.optionPrice(obj("""{"price":"10","quantity":0}"""))) // 0 || 1
        assertEquals(22.0, ItemPrice.optionPrice(obj("""{"price":"25","discounted_price":22.5}""")))
    }

    @Test
    fun `package codes`() {
        assertEquals(0.2, PackageCode.remit("r_20_10"))
        assertEquals(0.1, PackageCode.deliverySubsidy("r_20_10"))
        assertEquals(0.05, PackageCode.billing("p_10_5"))
        assertEquals(0.0, PackageCode.deliverySubsidy("apple"))
        assertTrue(PackageCode.deliverySubsidy("r_x_y").isNaN())
    }

    @Test
    fun `r_20_10 delivery, worked example of pricing md 14`() {
        val q = Pricing.quote(
            QuoteRequest(
                shop = shop, lines = lines, fulfilment = "delivery", nowEpochMs = at(12),
                fleets = Fleets(cmExpress, null), pickup = LatLon(18.79, 98.98), address = LatLon(18.8, 98.99),
                route = Route(4423.0, "google"),
            ),
        )
        assertEquals(listOf(120, 166), q.unitPrices)
        assertEquals(406, q.subtotal)
        assertEquals(53.0, q.fare!!.client)
        assertEquals(13.0, q.fare!!.remit)
        assertEquals(12, q.deliveryFee)
        assertEquals(530, q.freeDeliveryOver)
        assertEquals(418, q.total)
        val order = q.order!!
        assertEquals(41.0, order.order.deliverySubsidy)
        assertEquals(40.0, order.order.remit) // 81 − 41
        assertEquals("delayed", order.delivery!!.type)
        assertEquals(15.0, order.delivery!!.delayDuration)
        assertNull(q.webOnly)
        assertEquals(JsonPrimitive(40), order.toJson()["order"]!!.jsonObject["remit"])
    }

    @Test
    fun `cart screen quote without an address`() {
        val q = Pricing.quote(QuoteRequest(shop = shop.copy(fruit = "apple", freeDeliveryOver = 500.0), lines = lines, fulfilment = null, nowEpochMs = at(12)))
        assertEquals(406, q.total) // no fee yet
        assertNull(q.fare)
        assertEquals(500, q.freeDeliveryOver) // does not depend on the distance
        assertEquals(94, q.freeDeliveryRemainder)
        assertTrue(BlockReason.NO_FULFILMENT in q.blocked)
        assertNull(q.order)
        // With a subsidy the threshold depends on the fare, so it is unknown before the address.
        assertNull(Pricing.quote(QuoteRequest(shop = shop, lines = lines, fulfilment = "delivery", nowEpochMs = at(12))).freeDeliveryOver)
    }

    @Test
    fun `take-away has no fare and no subsidy (port decision 1)`() {
        val q = Pricing.quote(QuoteRequest(shop = shop.copy(takeawayDiscount = 15.0), lines = lines, fulfilment = "take-away", nowEpochMs = at(12), address = LatLon(18.8, 98.99), route = Route(4423.0, "google")))
        assertNull(q.fare)
        assertEquals(61, q.fulfilmentDiscount) // round(0.15 × 406) = 60.9
        assertEquals(0.0, q.order!!.order.deliverySubsidy)
        assertEquals(345, q.total)
    }

    @Test
    fun `free_delivery without subsidy shows no fee (port decision 2)`() {
        val q = Pricing.quote(
            QuoteRequest(
                shop = ShopSettings(fruit = "apple", freeDelivery = true, express = "x"), lines = lines, fulfilment = "delivery",
                nowEpochMs = at(12), fleets = Fleets(cmExpress, null), address = LatLon(0.0, 0.0), route = Route(4000.0, "google"),
            ),
        )
        assertEquals(456.0, q.web.totalValue) // the web checkout adds the fee
        assertEquals(406, q.total) // the order page does not
        assertEquals(0, q.deliveryFee)
    }

    @Test
    fun `peak hour and cash hours use Chiang Mai time`() {
        fun quote(h: Int, m: Int) = Pricing.quote(QuoteRequest(shop = shop, lines = lines, fulfilment = "delivery", nowEpochMs = at(h, m), fleets = Fleets(cmExpress, null), address = LatLon(0.0, 0.0), route = Route(27500.0, "google")))
        assertEquals(DistanceCheck.OK, quote(16, 59).distanceCheck)
        assertEquals(DistanceCheck.TOO_FAR, quote(17, 0).distanceCheck) // max 27 km at peak hours
        assertTrue(quote(22, 20).cashAllowed)
        assertTrue(!quote(22, 21).cashAllowed)
        assertTrue(!quote(8, 59).cashAllowed)
    }

    @Test
    fun `min order and big order rules`() {
        val small = Pricing.quote(QuoteRequest(shop = shop.copy(minOrder = 500.0), lines = lines, fulfilment = "delivery", nowEpochMs = at(12), fleets = Fleets(cmExpress, null), address = LatLon(0.0, 0.0), route = Route(2000.0, "google")))
        assertEquals(listOf(BlockReason.MIN_ORDER), small.blocked)
        val big = Pricing.quote(QuoteRequest(shop = shop.copy(deliveryType = "normal", delayDuration = 0.0), lines = listOf(BasketLine(obj("""{"id":"b","price":"1000"}"""), 1)), fulfilment = "delivery", nowEpochMs = at(12), fleets = Fleets(cmExpress, null), address = LatLon(0.0, 0.0), route = Route(2000.0, "google")))
        assertEquals("delayed", big.order!!.delivery!!.type)
        assertEquals(26.0, big.order!!.delivery!!.delayDuration)
    }

    @Test
    fun `fleet choice - fallback for the price, express for the remit`() {
        val fallback = Fleet(pricingArray = List(20) { 50.0 + it }, surge = 5.0, remitAmount = 7.0, peakHourMaxDistance = 27.0)
        val q = Pricing.quote(QuoteRequest(shop = ShopSettings(express = "e"), lines = lines, fulfilment = "delivery", nowEpochMs = at(12), fleets = Fleets(cmExpress, fallback), address = LatLon(0.0, 0.0), route = Route(1400.0, "google")))
        val fare = q.fare!!
        assertEquals(55.0, fare.cash) // fallback step 0 (50) + fallback surge (5)
        assertEquals(10.0, fare.remit) // express remit
        assertEquals(20.0, q.maxDeliveryDistanceKm) // fallback array length
    }

    @Test
    fun `web only flags`() {
        assertEquals(WebOnlyReason.LALAMOVE, Pricing.quote(QuoteRequest(shop = ShopSettings(express = "Lalamove Chiang Mai"), lines = lines, fulfilment = "take-away", nowEpochMs = at(12))).webOnly)
        assertEquals(WebOnlyReason.INVALID_AMOUNT, Pricing.quote(QuoteRequest(shop = ShopSettings(), lines = listOf(BasketLine(obj("""{"id":"b","price":"abc"}"""), 1)), fulfilment = "take-away", nowEpochMs = at(12))).webOnly)
    }

    @Test
    fun `cart adapter builds the web basket item`() {
        val group = obj("""{"name":"Size","required":true,"select":"single","items":[{"id":"L","name":"Large","price":"20"}]}""")
        val line = CartLine(
            lineId = "line-1", itemId = "i2", quantity = 3, options = listOf(CartOption("g1", "L", 2)), note = "no ice",
            item = obj("""{"id":"i2","name":"B","price":"100","discount":10,"options":["g1"]}"""), groups = mapOf("g1" to group),
        )
        val cart = Cart(businessId = "b", workflowId = "w", createdAt = 0, fulfilment = "take-away", lines = listOf(line))
        val item = CartPricing.webItem(line, ShopSettings())
        assertEquals("line-1", item["id2"]!!.jsonPrimitive.content)
        assertEquals("no ice", item["comment"]!!.jsonPrimitive.content)
        val option = item["options"]!!.jsonArray[0].jsonObject
        assertEquals("g1", option["menu"]!!.jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals(null, option["menu"]!!.jsonObject["items"])
        assertEquals(2, option["option"]!!.jsonObject["quantity"]!!.jsonPrimitive.content.toInt())
        val q = Pricing.quote(CartPricing.request(cart, ShopSettings(), at(12)))
        assertEquals(listOf(130), q.unitPrices) // 90 + 2 × 20
        assertEquals(390, q.subtotal)
        // propagate_discounts: the option also gets 10 % off (18, not rounded), and the visual discount changes the item.
        val shop = ShopSettings(propagateDiscounts = true)
        assertEquals(126, Pricing.quote(CartPricing.request(cart, shop, at(12))).unitPrices[0]) // 90 + 2 × 18
        val visual = ShopSettings(visualDiscount = 0.1)
        assertEquals(listOf(129), Pricing.quote(CartPricing.request(cart, visual, at(12))).unitPrices) // 111 − 20 % = 89 (88.8) + 40
    }
}
