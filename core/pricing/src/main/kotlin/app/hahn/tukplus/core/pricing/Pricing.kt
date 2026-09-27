package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.domain.ChiangMaiTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor

enum class DistanceCheck { OK, TOO_SHORT, TOO_FAR }

/** Why the order cannot be placed now. The web app checks them in this order. */
enum class BlockReason {
    EMPTY_BASKET,
    /** `min_order` (delivery only). */
    MIN_ORDER,
    /** The basket has the free gift, but the basket value is below the gift threshold. */
    FREE_GIFT_BELOW_THRESHOLD,
    NO_FULFILMENT,
    NO_ADDRESS,
    DISTANCE_TOO_SHORT,
    DISTANCE_TOO_FAR,
}

/** Why the native checkout cannot be used for this shop or basket. The app then offers only the web checkout. */
enum class WebOnlyReason {
    /** The shop uses Lalamove. The fare comes from a Lalamove quote. */
    LALAMOVE,
    /** The shop has several pickup locations. */
    MULTIPLE_LOCATIONS,
    /** An amount is not a number in the web code (bad price or package code). */
    INVALID_AMOUNT,
    /** An amount has a fraction. The native screens and the order body use whole baht. */
    FRACTIONAL_AMOUNT,
}

/** The delivery fare object (`data.fare` of the order). All values in baht, [distance] in metres. */
data class Fare(
    val price: Double,
    val discount: Double,
    val cash: Double,
    val tukpay: Double,
    val bonus: Double,
    val distance: Double,
    /** "graphhopper", "google" or "estimate". */
    val source: String,
    val remit: Double,
    val client: Double,
) {
    val version: String get() = "v1"
}

/**
 * The web values with their web names, as numbers (NaN where the web code gives NaN).
 * The screens use [Quote]; these are for the order body, tests and support.
 */
data class WebValues(
    /** `discountedTotalPrice(line.item)` of each line. */
    val lineUnits: List<Double>,
    val basketValue: Double,
    val vatableBasketValue: Double,
    val vat: Double,
    val fulfilmentDiscountPercent: Double,
    val fulfilmentDiscountAmount: Double,
    val maxDeliveryDistance: Double,
    val isPeakHour: Boolean,
    val deliverySubsidyPercent: Double,
    val deliverySubsidy: Double,
    val actualDeliverySubsidy: Double,
    val remitPercent: Double,
    val remitAmount: Double,
    val actualRemitAmount: Double,
    val specialRemit: Double,
    val specialDiscount: Double,
    val billingPercent: Double,
    /** `(basket value × billing).toFixed(2)`. */
    val billingAmount: String,
    val freeDelivery: Boolean,
    val fixedFreeDeliveryOver: Double?,
    val dynamicFreeDeliveryOver: Double?,
    val freeDeliveryOver: Double?,
    val freeDeliveryRemainder: Double?,
    val insideFreeDeliveryPolygon: Boolean,
    val isLegacyFreeDelivery: Boolean,
    val isFreeDelivery: Boolean,
    /** The fee in the web basket (and in the web total). Null without a fare. */
    val actualDeliveryFee: Double?,
    /** The fee in the web "Fees" sheet. */
    val deliveryFee: Double,
    /** The web checkout total (`totalValue`). */
    val totalValue: Double,
)

/** All amounts for the cart and checkout screens. Amounts are whole baht; null means "not a number". */
data class Quote(
    /** The unit price of each line: item price after discount plus its options. */
    val unitPrices: List<Int?>,
    /** Unit price × quantity of each line. */
    val lineTotals: List<Int?>,
    val subtotal: Int?,
    val itemCount: Int,
    val vat: Int?,
    val fulfilmentDiscount: Int?,
    /** Delivery only, when there is an address. */
    val distanceCheck: DistanceCheck?,
    val maxDeliveryDistanceKm: Double,
    val isPeakHour: Boolean,
    /** Delivery only, with an accepted address. */
    val fare: Fare?,
    /** The delivery fee that the customer pays (after the subsidy). Null without a fare. */
    val deliveryFee: Int?,
    val isFreeDelivery: Boolean,
    /**
     * The basket value for free delivery, or null. Without an address it is given only when it
     * does not depend on the distance.
     */
    val freeDeliveryOver: Int?,
    /** Baht more for free delivery ("Only ฿X more!"), or null. Can be 0 or less ("You got it!"). */
    val freeDeliveryRemainder: Int?,
    /** subtotal + VAT − discount + fee. The same total as the order page (port decision 2). */
    val total: Int?,
    val blocked: List<BlockReason>,
    val cashAllowed: Boolean,
    /** Non-null: offer only the web checkout. */
    val webOnly: WebOnlyReason?,
    /** The amounts for `POST transactions`. Null when [blocked] is not empty. */
    val order: OrderAmounts?,
    val web: WebValues,
)

/** The price rules of the Tuk web app (docs/pricing.md). */
object Pricing {
    /** Orders of this basket value or more are sent as "delayed" (26 minutes). */
    const val BIG_ORDER = 1000.0
    const val BIG_ORDER_DELAY = 26.0

    fun quote(r: QuoteRequest): Quote {
        val s = r.shop
        val local = Instant.ofEpochMilli(r.nowEpochMs).atZone(ChiangMaiTime.ZONE)
        val isDelivery = r.fulfilment == "delivery"
        val isTakeaway = r.fulfilment == "take-away"
        val isDinein = r.fulfilment == "dine-in"

        // Basket (§4, §5).
        val units = r.lines.map { ItemPrice.lineUnit(it.item) }
        var bv = 0.0
        var vatable = 0.0
        r.lines.forEachIndexed { i, line ->
            val t = units[i] * line.quantity
            bv += t
            val v = line.item["vatable"]
            if (!(line.item.containsKey("vatable") && !Js.truthy(v))) vatable += t
        }
        val itemCount = r.lines.sumOf { it.quantity }
        val vat = if (Js.truthy(s.vat)) Js.round(s.vat!! * vatable) else 0.0

        // Take-away and dine-in discount (§6).
        var e = 0.0
        if (isTakeaway && Js.truthy(s.takeawayDiscount)) e = s.takeawayDiscount!!
        if (isDinein && Js.truthy(s.dineinDiscount)) e = s.dineinDiscount!!
        val fdPercent = e / 100
        val fdAmount = if (Js.truthy(fdPercent)) Js.round(fdPercent * bv) else 0.0

        // Fleets (§8.1).
        val dw = r.fleets
        val isPeakHour = local.hour == 17 || local.hour == 18
        val pricingArray = when {
            dw == null -> null
            dw.fallback != null -> dw.fallback.pricingArray
            dw.express != null -> dw.express.pricingArray
            else -> null
        }
        val surge = when {
            dw == null -> 0.0
            dw.fallback != null -> dw.fallback.surge.orZero()
            else -> dw.express?.surge.orZero()
        }
        val peakMax = if (isPeakHour && dw != null) {
            when {
                dw.fallback != null -> dw.fallback.peakHourMaxDistance
                dw.express != null -> dw.express.peakHourMaxDistance
                else -> null
            }
        } else null
        val fleetRemit: Double? = when {
            dw == null -> null
            dw.express != null && Js.truthy(dw.express.remitAmount) -> dw.express.remitAmount!!
            else -> dw.fallback?.remitAmount.orZero()
        }
        val customerRemit = r.user?.remitAmount.orZero()
        val shopRemit = s.shopRemit.orZero()
        val deliveryRemit = ((fleetRemit ?: 0.0) + customerRemit + shopRemit).let { if (Js.truthy(it)) it else 0.0 }
        val extraDistance = r.user?.extraDistance.orZero()
        val extraCash = s.extraCash.orZero()
        val extraTukpay = s.extraTukpay.orZero()
        val maxDistance = listOfNotNull(s.maxDistance, peakMax, pricingArray?.size?.toDouble())
            .filter { Js.truthy(it) }
            .let { if (it.isEmpty()) 14.0 else it.min() }
        val isLalamove = s.express.orEmpty().startsWith("Lalamove") || s.fallbackFleet.orEmpty().startsWith("Lalamove")

        // Distance and fare (§7, §8).
        var distanceCheck: DistanceCheck? = null
        var fare: Fare? = null
        if (isDelivery && r.address != null) {
            val metres = if (r.route != null) {
                r.route.distance + extraDistance
            } else {
                val straight = if (r.pickup == null) 999e3 else Geo.straightMetres(r.pickup, r.address)
                Js.round(1.25 * straight) + extraDistance
            }
            distanceCheck = when {
                metres <= 50 -> DistanceCheck.TOO_SHORT
                metres > 1e3 * maxDistance -> DistanceCheck.TOO_FAR
                else -> DistanceCheck.OK
            }
            if (distanceCheck == DistanceCheck.OK) {
                val n = when {
                    pricingArray != null -> priceFromArray(pricingArray, metres)
                    s.express == "self" && Js.truthy(s.deliveryFeePerKm) -> {
                        val x = Js.round(metres / 1e3 * s.deliveryFeePerKm!!)
                        if (s.minDeliveryFee != null && x < s.minDeliveryFee) s.minDeliveryFee else x
                    }
                    else -> defaultPrice(metres)
                }
                val cash = n + extraCash + surge
                fare = Fare(
                    price = n + surge + extraTukpay + extraCash,
                    discount = 0.0,
                    cash = cash,
                    tukpay = extraTukpay,
                    bonus = extraCash + extraTukpay + surge,
                    distance = metres,
                    source = r.route?.source ?: "estimate",
                    remit = deliveryRemit,
                    client = cash + deliveryRemit,
                )
            }
        }

        // Package code, subsidy and free delivery (§9, §10).
        val pkg = s.fruit
        val subsidyPercent = PackageCode.deliverySubsidy(pkg)
        val subsidy = Js.round(bv * subsidyPercent)
        val dynamicOver = if (fare == null || !Js.truthy(subsidyPercent)) null else ceil(fare.client / subsidyPercent)
        val fixedOver = if (fare == null || !Js.truthy(s.freeDeliveryOver)) null
        else if (Js.truthy(s.freeDeliveryDistance) && fare.distance > s.freeDeliveryDistance!!) null
        else s.freeDeliveryOver
        val freeOver = when {
            Js.truthy(fixedOver) && Js.truthy(dynamicOver) -> if (dynamicOver!! < fixedOver!!) dynamicOver else fixedOver
            Js.truthy(dynamicOver) -> dynamicOver
            Js.truthy(subsidyPercent) -> null
            else -> fixedOver
        }
        val freeDelivery = Js.truthy(subsidyPercent) || Js.truthy(freeOver) || s.freeDelivery == true
        val inside = Js.truthy(s.freeDeliveryPolygon) && r.address != null && Geo.inPolygon(r.address, s.freeDeliveryPolygon)
        val isLegacy = inside || (Js.truthy(freeOver) && bv >= freeOver!!)
        val actualFee: Double? = when {
            fare == null -> null
            isLegacy -> 0.0
            Js.truthy(subsidy) -> if (subsidy > fare.client) 0.0 else fare.client - subsidy
            else -> fare.client
        }
        val isFree = freeDelivery && (Js.gte(bv, freeOver) || (actualFee != null && actualFee == 0.0) || inside)
        val remainder = if (Js.truthy(freeOver)) freeOver!! - bv else null

        // Commission and billing (§11).
        val billingPkg = PackageCode.billing(pkg)
        val billingAmount = Js.toFixed2(bv * billingPkg)
        val remitPkg = PackageCode.remit(pkg)
        val remitAmount = Js.round(bv * remitPkg).let { if (Js.truthy(s.maxRemit) && it > s.maxRemit!!) s.maxRemit else it }
        val actualSubsidy = when {
            fare == null -> 0.0
            isFree || fare.client < subsidy -> fare.client
            else -> subsidy
        }
        val actualRemit = if (!isDelivery || !Js.truthy(remitAmount)) 0.0 else (remitAmount - actualSubsidy).let { if (it < 0) 0.0 else it }
        val specialRemit = if (pkg != null && !isLalamove && pkg.startsWith("p_") && fare != null && isFree) {
            val x = subsidy - fare.client
            if (x < 0) 0.0 else if (x > 30) 30.0 else x
        } else 0.0
        val specialDiscount = if (pkg != null && pkg.startsWith("f_") && fare != null && isFree) subsidy - fare.client else 0.0
        val webDeliveryFee = when {
            fare == null -> 0.0
            isFree -> 0.0
            Js.truthy(subsidy) -> if (subsidy > fare.client) 0.0 else fare.client - subsidy
            else -> fare.client
        }
        val base = bv + vat - fdAmount
        val webTotal = if (isDelivery) base + (actualFee ?: 0.0) else base

        // Checks (§13).
        val blocked = mutableListOf<BlockReason>()
        if (r.lines.isEmpty() || itemCount <= 0) blocked += BlockReason.EMPTY_BASKET
        if (Js.truthy(s.minOrder) && bv < s.minOrder!! && isDelivery) blocked += BlockReason.MIN_ORDER
        val isFreeGiftOrder = r.lines.any { Js.truthy(it.item["free_gift"]) }
        val freeGiftOver = r.menuFreeGift?.let { Js.parseInt(it) }
        if (isFreeGiftOrder && bv < (freeGiftOver ?: 0.0)) blocked += BlockReason.FREE_GIFT_BELOW_THRESHOLD
        if (r.fulfilment == null) blocked += BlockReason.NO_FULFILMENT
        if (isDelivery && r.address == null) blocked += BlockReason.NO_ADDRESS
        if (distanceCheck == DistanceCheck.TOO_SHORT) blocked += BlockReason.DISTANCE_TOO_SHORT
        if (distanceCheck == DistanceCheck.TOO_FAR) blocked += BlockReason.DISTANCE_TOO_FAR

        val hhmm = local.hour * 100 + local.minute
        val cashAllowed = !(isDelivery && hhmm < 900) && !(isDelivery && hhmm > 2220) &&
            (s.paymentOptions == null || "cash" in s.paymentOptions)

        // Order body amounts (§14). Port decision 1: no fare and no subsidy unless delivery.
        val settings = OrderSettings(
            vatPercent = if (Js.truthy(s.vat)) s.vat!! else 0.0,
            billingPercent = if (isDelivery) billingPkg else 0.0,
            remitPercent = if (isDelivery) remitPkg else 0.0,
            deliverySubsidyPercent = subsidyPercent,
            fulfilmentDiscountPercent = fdPercent,
        )
        val deliveryFields = if (isDelivery) {
            val delayed = !r.fulfilmentTime.isNullOrEmpty() || s.deliveryType == "delayed"
            var type: String? = if (delayed) "delayed" else null
            var delay: Double? = if (Js.truthy(s.delayDuration)) s.delayDuration else null
            if (!delayed && bv >= BIG_ORDER) {
                type = "delayed"
                delay = BIG_ORDER_DELAY
            }
            if (s.deliveryType == "third-party") type = "third-party"
            DeliveryFields(
                workflowId = s.express,
                expressFleet = s.express,
                fallbackFleet = s.fallbackFleet?.takeIf { it.isNotEmpty() },
                isFreeDelivery = isFree,
                type = type,
                delayDuration = delay,
                reversed = if (s.reversed == true) true else null,
            )
        } else null
        val orderValues = OrderValues(
            orderValue = bv,
            paymentMethod = r.paymentMethod,
            deliverySubsidy = actualSubsidy,
            remit = actualRemit + specialDiscount + specialRemit,
            fulfilmentDiscount = fdAmount,
            specialDiscount = 0.0,
            vat = vat,
            fulfilmentTime = r.fulfilmentTime?.takeIf { it.isNotEmpty() },
        )
        val billingSent = if (isDelivery && (ItemPrice.parseFloat(JsonPrimitive(billingAmount)) > 0)) billingAmount else null
        val amounts = OrderAmounts(
            type = r.fulfilment.orEmpty(),
            order = orderValues,
            settings = settings,
            delivery = deliveryFields,
            fare = if (isDelivery) fare else null,
            billing = billingSent,
            autoconfirm = if (s.autoconfirm == true) true else null,
        )

        // The order page total (port decision 2): the fee is 0 when the order says "free delivery".
        // The page reads the sent JSON, so NaN amounts are null there and count as 0.
        val pageFee = if (isDelivery && fare != null && !isFree) {
            (if (Js.truthy(fare.client)) fare.client else fare.cash) - nz(actualSubsidy)
        } else if (isDelivery && fare != null) 0.0 else null
        val total = nz(bv) + nz(vat) - (0.0 + nz(fdAmount) + 0.0) + (pageFee ?: 0.0)

        // Free delivery threshold for the screens. Without a fare, give it only when it does not depend on the distance.
        val shownOver: Double? = if (fare != null) freeOver
        else if ((isDelivery || r.fulfilment == null) && !Js.truthy(subsidyPercent) && Js.truthy(s.freeDeliveryOver) && !Js.truthy(s.freeDeliveryDistance)) s.freeDeliveryOver
        else null

        val moneyValues = listOf(bv, vat, fdAmount, total, actualSubsidy, orderValues.remit) + units + listOfNotNull(pageFee) +
            listOfNotNull(fare?.price, fare?.cash, fare?.client, fare?.remit)
        val webOnly = when {
            isLalamove -> WebOnlyReason.LALAMOVE
            s.hasLocations -> WebOnlyReason.MULTIPLE_LOCATIONS
            moneyValues.any { it.isNaN() || it.isInfinite() } || subsidyPercent.isNaN() || remitPkg.isNaN() || billingPkg.isNaN() -> WebOnlyReason.INVALID_AMOUNT
            moneyValues.any { it != floor(it) } -> WebOnlyReason.FRACTIONAL_AMOUNT
            else -> null
        }

        val web = WebValues(
            lineUnits = units,
            basketValue = bv,
            vatableBasketValue = vatable,
            vat = vat,
            fulfilmentDiscountPercent = fdPercent,
            fulfilmentDiscountAmount = fdAmount,
            maxDeliveryDistance = maxDistance,
            isPeakHour = isPeakHour,
            deliverySubsidyPercent = subsidyPercent,
            deliverySubsidy = subsidy,
            actualDeliverySubsidy = actualSubsidy,
            remitPercent = settings.remitPercent,
            remitAmount = remitAmount,
            actualRemitAmount = actualRemit,
            specialRemit = specialRemit,
            specialDiscount = specialDiscount,
            billingPercent = settings.billingPercent,
            billingAmount = billingAmount,
            freeDelivery = freeDelivery,
            fixedFreeDeliveryOver = fixedOver,
            dynamicFreeDeliveryOver = dynamicOver,
            freeDeliveryOver = freeOver,
            freeDeliveryRemainder = remainder,
            insideFreeDeliveryPolygon = inside,
            isLegacyFreeDelivery = isLegacy,
            isFreeDelivery = isFree,
            actualDeliveryFee = actualFee,
            deliveryFee = webDeliveryFee,
            totalValue = webTotal,
        )
        return Quote(
            unitPrices = units.map { it.money() },
            lineTotals = units.mapIndexed { i, u -> (u * r.lines[i].quantity).money() },
            subtotal = bv.money(),
            itemCount = itemCount,
            vat = vat.money(),
            fulfilmentDiscount = fdAmount.money(),
            distanceCheck = distanceCheck,
            maxDeliveryDistanceKm = maxDistance,
            isPeakHour = isPeakHour,
            fare = fare,
            deliveryFee = pageFee.moneyOrNull(),
            isFreeDelivery = isFree,
            freeDeliveryOver = shownOver.moneyOrNull(),
            freeDeliveryRemainder = shownOver?.let { it - bv }.moneyOrNull(),
            total = total.money(),
            blocked = blocked,
            cashAllowed = cashAllowed,
            webOnly = webOnly,
            order = if (blocked.isEmpty()) amounts else null,
            web = web,
        )
    }

    /** `getPriceFromPricingArray`: step `round(m / 1000) − 1` (0 stays 0). NaN past the end. */
    internal fun priceFromArray(array: List<Double>, metres: Double): Double {
        var km = Js.round(metres / 1e3)
        if (Js.truthy(km)) km -= 1
        if (km.isNaN() || km < 0 || km != floor(km)) return Double.NaN
        return array.getOrNull(km.toInt()) ?: Double.NaN
    }

    /** `getDefaultDeliveryPrice`: the price when the shop has no fleet. */
    internal fun defaultPrice(metres: Double): Double {
        var t = 80.0
        if (metres < 1e4) t = 60.0
        if (metres < 5e3) t = 40.0
        if (metres < 3e3) t = 30.0
        return t
    }

    /** A number after a JSON round trip, read with `|| 0` or `+`: NaN (sent as null) is 0. */
    private fun nz(x: Double): Double = if (x.isNaN()) 0.0 else x

    private fun Double?.orZero(): Double = if (Js.truthy(this)) this!! else 0.0
}

/** The free gift threshold of a menu: `free_gift` of the first gift item that is in stock and not hidden. */
fun menuFreeGift(items: List<JsonObject>): kotlinx.serialization.json.JsonElement? =
    items.firstOrNull { Js.truthy(it["free_gift"]) && !Js.truthy(it["out_of_stock"]) && !Js.truthy(it["hidden"]) }?.get("free_gift")
