package app.hahn.tukplus.core.pricing

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The amount fields of the `POST transactions` body (docs/pricing.md §14). The rest of the
 * body (business, address, basket items, uuid, short id) is not about prices.
 * Numbers are JavaScript numbers; [toJson] writes them like `JSON.stringify` (NaN → null).
 */
data class OrderAmounts(
    /** `data.type`. */
    val type: String,
    val order: OrderValues,
    val settings: OrderSettings,
    /** `data.delivery` (amount-related fields). Null unless delivery. */
    val delivery: DeliveryFields?,
    /** `data.fare`. Null unless delivery. */
    val fare: Fare?,
    /** `data.billing` text, for example "19.00". Null: not sent. */
    val billing: String?,
    /** `data.autoconfirm`. Null: not sent. */
    val autoconfirm: Boolean?,
) {
    /** The fields for `data`: `order` (without `basket`), `settings`, `delivery` (amount fields), `fare`, `billing`, `autoconfirm`. */
    fun toJson(): JsonObject = buildJsonObject {
        put("type", type)
        put("order", order.toJson())
        put("settings", settings.toJson())
        delivery?.let { put("delivery", it.toJson()) }
        fare?.let { put("fare", it.toJson()) }
        billing?.let { put("billing", it) }
        autoconfirm?.let { put("autoconfirm", it) }
    }
}

/** `data.order` amounts. */
data class OrderValues(
    val orderValue: Double,
    val paymentMethod: String,
    val deliverySubsidy: Double,
    val remit: Double,
    val fulfilmentDiscount: Double,
    val specialDiscount: Double,
    val vat: Double,
    /** "HH:MM", scheduled orders only. */
    val fulfilmentTime: String?,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("order_value", Js.json(orderValue))
        put("payment_method", paymentMethod)
        put("delivery_subsidy", Js.json(deliverySubsidy))
        put("remit", Js.json(remit))
        put("fulfilment_discount", Js.json(fulfilmentDiscount))
        put("special_discount", Js.json(specialDiscount))
        put("vat", Js.json(vat))
        fulfilmentTime?.let { put("fulfilment_time", it) }
    }
}

/** `data.settings` (fractions). */
data class OrderSettings(
    val vatPercent: Double,
    val billingPercent: Double,
    val remitPercent: Double,
    val deliverySubsidyPercent: Double,
    val fulfilmentDiscountPercent: Double,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("vat_percent", Js.json(vatPercent))
        put("billing_percent", Js.json(billingPercent))
        put("remit_percent", Js.json(remitPercent))
        put("delivery_subsidy_percent", Js.json(deliverySubsidyPercent))
        put("fulfilment_discount_percent", Js.json(fulfilmentDiscountPercent))
    }
}

/** `data.delivery` fields that depend on the price rules. Null fields are not sent. */
data class DeliveryFields(
    /** The fleet name (`workflow.data.express`). */
    val workflowId: String?,
    val expressFleet: String?,
    val fallbackFleet: String?,
    val isFreeDelivery: Boolean,
    /** "delayed", "third-party", or null (not sent). */
    val type: String?,
    val delayDuration: Double?,
    val reversed: Boolean?,
) {
    fun toJson(): JsonObject = buildJsonObject {
        workflowId?.let { put("workflow_id", it) }
        expressFleet?.let { put("express_fleet", it) }
        fallbackFleet?.let { put("fallback_fleet", it) }
        put("is_free_delivery", isFreeDelivery)
        type?.let { put("type", it) }
        delayDuration?.let { put("delay_duration", Js.json(it)) }
        reversed?.let { put("reversed", it) }
    }
}

fun Fare.toJson(): JsonObject = buildJsonObject {
    put("version", version)
    put("price", Js.json(price))
    put("discount", Js.json(discount))
    put("cash", Js.json(cash))
    put("tukpay", Js.json(tukpay))
    put("bonus", Js.json(bonus))
    put("distance", Js.json(distance))
    put("source", source)
    put("remit", Js.json(remit))
    put("client", Js.json(client))
}
