package app.hahn.tukplus.core.pricing

import app.hahn.tukplus.core.model.CommerceDelivery
import app.hahn.tukplus.core.model.DeliveryFleet
import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.model.WorkflowData
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The shop settings that the price rules read (Commerce `workflow.data`). */
data class ShopSettings(
    val fruit: String? = null,
    val vat: Double? = null,
    val minOrder: Double? = null,
    val takeawayDiscount: Double? = null,
    val dineinDiscount: Double? = null,
    val maxRemit: Double? = null,
    val freeDelivery: Boolean? = null,
    val freeDeliveryOver: Double? = null,
    val freeDeliveryDistance: Double? = null,
    val freeDeliveryPolygon: JsonElement? = null,
    val maxDistance: Double? = null,
    val visualDiscount: Double? = null,
    val propagateDiscounts: Boolean = false,
    /** Fleet name, or "self". */
    val express: String? = null,
    val fallbackFleet: String? = null,
    val paymentOptions: List<String>? = null,
    val deliveryFeePerKm: Double? = null,
    val minDeliveryFee: Double? = null,
    val deliveryType: String? = null,
    val delayDuration: Double? = null,
    val shopRemit: Double? = null,
    val extraCash: Double? = null,
    val extraTukpay: Double? = null,
    val reversed: Boolean? = null,
    val autoconfirm: Boolean? = null,
    val hasLocations: Boolean = false,
) {
    companion object {
        fun from(data: WorkflowData?): ShopSettings {
            if (data == null) return ShopSettings()
            val d = data.deliveryOptions
            return ShopSettings(
                fruit = data.fruit,
                vat = data.vat,
                minOrder = data.minOrder?.toDouble(),
                takeawayDiscount = data.takeawayDiscount,
                dineinDiscount = data.dineinDiscount,
                maxRemit = data.maxRemit,
                freeDelivery = data.freeDelivery,
                freeDeliveryOver = data.freeDeliveryOver?.toDouble(),
                freeDeliveryDistance = data.freeDeliveryDistance,
                freeDeliveryPolygon = data.freeDeliveryPolygon,
                maxDistance = data.maxDistance,
                visualDiscount = data.visualDiscount,
                propagateDiscounts = data.menuOptions?.propagateDiscounts == true,
                express = data.express,
                fallbackFleet = data.fallbackFleet,
                paymentOptions = data.paymentOptions,
                deliveryFeePerKm = data.deliveryFeePerKm,
                minDeliveryFee = data.minDeliveryFee,
                deliveryType = d?.type,
                delayDuration = d?.delayDuration?.toDouble(),
                shopRemit = d?.remitAmount,
                extraCash = d?.extraCash,
                extraTukpay = d?.extraTukpay,
                reversed = d?.reversed,
                autoconfirm = data.orderOptions?.autoconfirm,
                hasLocations = Js.truthy(data.locations),
            )
        }
    }
}

/** One delivery fleet (`workflows?commerce_delivery=`). */
data class Fleet(
    val pricingArray: List<Double>? = null,
    val surge: Double? = null,
    val remitAmount: Double? = null,
    val peakHourMaxDistance: Double? = null,
) {
    companion object {
        fun from(fleet: DeliveryFleet?): Fleet? = fleet?.data?.let {
            Fleet(it.pricingArray, it.surge, it.remitAmount, it.peakHourMaxDistance)
        } ?: fleet?.let { Fleet() }
    }
}

/** The fleets of the shop. Null in [QuoteRequest.fleets] means "not loaded" (the web code then uses a default price). */
data class Fleets(val express: Fleet?, val fallback: Fleet?) {
    companion object {
        fun from(delivery: CommerceDelivery): Fleets = Fleets(Fleet.from(delivery.express), Fleet.from(delivery.fallback))
    }
}

/** The per-user delivery settings (`user.data.delivery_options`). Usually absent. */
data class UserDeliveryOptions(val remitAmount: Double? = null, val extraDistance: Double? = null)

/** A driving route from the pickup point to the address. */
data class Route(
    /** Metres. */
    val distance: Double,
    /** "graphhopper" or "google". */
    val source: String,
)

/**
 * One basket line in the web basket form: [item] is the menu item with `options` =
 * `[{menu, option}]`, `id2` and `comment` (see [CartPricing] to make it from a cart line).
 */
data class BasketLine(val item: JsonObject, val quantity: Int)

/** Everything that the price rules need. */
data class QuoteRequest(
    val shop: ShopSettings,
    val lines: List<BasketLine>,
    /** "delivery", "take-away", "dine-in", or null (not chosen yet). */
    val fulfilment: String?,
    /** The time, epoch milliseconds. Peak and cash hours use Chiang Mai time (ChiangMaiTime.ZONE). */
    val nowEpochMs: Long,
    /** Null: the fleets are not loaded. */
    val fleets: Fleets? = null,
    /** The shop location (pickup point). */
    val pickup: LatLon? = null,
    /** The delivery address. Null: no address yet (cart screen). */
    val address: LatLon? = null,
    /** The driving route. Null with an address: the straight line × 1.25 is used. */
    val route: Route? = null,
    val user: UserDeliveryOptions? = null,
    val paymentMethod: String = "cash",
    /** "HH:MM" for a scheduled order. */
    val fulfilmentTime: String? = null,
    /** `free_gift` of the menu's free gift item (the basket value it needs), if the menu has one. */
    val menuFreeGift: JsonElement? = null,
)
