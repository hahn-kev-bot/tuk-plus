package app.hahn.tukplus.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A shop. From `businesses?type=eatery`, `businesses/{id}` and others. */
@Serializable
data class Business(
    val id: String,
    val name: String = "",
    val shortName: String? = null,
    val address: String? = null,
    val businessType: String? = null,
    val isActive: Boolean = true,
    val isApproved: Boolean = true,
    val isDeleted: Boolean = false,
    val lat: Double? = null,
    val lon: Double? = null,
    val country: String? = null,
    val profilePicUrl: String? = null,
    val bannerPicUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val data: BusinessData? = null,
    val workflows: List<Workflow>? = null,
) {
    /** The workflow that holds the menu and the shop settings, if the shop has one. */
    val commerceWorkflow: Workflow?
        get() = workflows?.firstOrNull { it.workflowTypeName == Workflow.TYPE_COMMERCE }
}

@Serializable
data class BusinessData(
    @Serializable(LenientBooleanSerializer::class) val hidden: Boolean? = null,
    /** Short handle such as "@murka". */
    val premiumLink: String? = null,
    /** Comma-separated. Entries that start with "_" are hidden keywords; entries that end with "*" are badges. */
    val categories: String? = null,
    val searchTerms: String? = null,
    val blurb: String? = null,
    val phoneNumber: String? = null,
    val website: String? = null,
    /** Day key ("mon", …, "sun") to a range text such as "0800-1600", or null / "" for closed. */
    val hours: Map<String, String?>? = null,
    /** "selected-hours" or "always-open". */
    val hoursType: String? = null,
    val productPicUrl: String? = null,
    val bannerStyle: String? = null,
    @Serializable(LenientBooleanSerializer::class) val award: Boolean? = null,
    val themeColor: String? = null,
)

/** A shop feature. For food orders the type is "Commerce"; the delivery fleet has type "Delivery". */
@Serializable
data class Workflow(
    val id: String,
    val businessId: String? = null,
    val workflowTypeName: String? = null,
    val name: String? = null,
    val state: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val isDeleted: Boolean = false,
    val data: WorkflowData? = null,
    val blobs: List<Blob>? = null,
) {
    companion object {
        const val TYPE_COMMERCE = "Commerce"
        const val TYPE_DELIVERY = "Delivery"
    }
}

/** Settings of a Commerce workflow. Only the fields that the customer app needs. */
@Serializable
data class WorkflowData(
    @Serializable(LenientBooleanSerializer::class) val paused: Boolean? = null,
    /** ISO time. The shop is on holiday until then. */
    val closedUntil: String? = null,
    val fulfilmentOptions: List<String>? = null,
    val paymentOptions: List<String>? = null,
    /** Shop package code, for example "r_20_10" (commission percent, delivery subsidy percent). */
    val fruit: String? = null,
    /** Name of the delivery fleet, for example "Chiang Mai Express Delivery". */
    val express: String? = null,
    val fallbackFleet: String? = null,
    val languages: List<String>? = null,
    @Serializable(LenientDoubleSerializer::class) val vat: Double? = null,
    @Serializable(LenientIntSerializer::class) val minOrder: Int? = null,
    @Serializable(LenientIntSerializer::class) val freeDeliveryOver: Int? = null,
    @Serializable(LenientIntSerializer::class) val takeawayDiscount: Int? = null,
    @Serializable(LenientIntSerializer::class) val dineinDiscount: Int? = null,
    val deliveryOptions: DeliveryOptions? = null,
    val menuStyle: String? = null,
    val refPrefix: String? = null,
    @Serializable(LenientBooleanSerializer::class) val test: Boolean? = null,
    val membership: JsonObject? = null,
    // Price settings (docs/pricing.md). Missing values mean "not set".
    /** Cap of the Tuk commission in baht. */
    @Serializable(LenientDoubleSerializer::class) val maxRemit: Double? = null,
    @Serializable(LenientBooleanSerializer::class) val freeDelivery: Boolean? = null,
    /** Metres. `free_delivery_over` applies only up to this distance. */
    @Serializable(LenientDoubleSerializer::class) val freeDeliveryDistance: Double? = null,
    /** GeoJSON polygon rings `[[[lon, lat], …]]`. Delivery inside it is free. */
    val freeDeliveryPolygon: JsonElement? = null,
    val freeDeliveryTo: String? = null,
    /** Kilometres. */
    @Serializable(LenientDoubleSerializer::class) val maxDistance: Double? = null,
    /** Fraction, for example 0.1. The menu shows higher prices with a bigger discount. */
    @Serializable(LenientDoubleSerializer::class) val visualDiscount: Double? = null,
    val menuOptions: MenuOptions? = null,
    /** Own delivery (`express` = "self"): baht per km. */
    @Serializable(LenientDoubleSerializer::class) val deliveryFeePerKm: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val minDeliveryFee: Double? = null,
    val orderOptions: OrderOptions? = null,
    /** Several pickup points. Not supported by the native checkout. */
    val locations: JsonElement? = null,
)

@Serializable
data class MenuOptions(
    /** The item's percent discount also applies to its option prices. */
    @Serializable(LenientBooleanSerializer::class) val propagateDiscounts: Boolean? = null,
)

@Serializable
data class OrderOptions(
    @Serializable(LenientBooleanSerializer::class) val autoconfirm: Boolean? = null,
)

@Serializable
data class DeliveryOptions(
    /** "delayed" or another value. */
    val type: String? = null,
    @Serializable(LenientIntSerializer::class) val delayDuration: Int? = null,
    /** Baht added to the delivery remit (the shop's part of the fare). */
    @Serializable(LenientDoubleSerializer::class) val remitAmount: Double? = null,
    /** Baht added to the fare for the driver (cash). */
    @Serializable(LenientDoubleSerializer::class) val extraCash: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val extraTukpay: Double? = null,
    @Serializable(LenientBooleanSerializer::class) val reversed: Boolean? = null,
    val driverNote: String? = null,
    val signName: String? = null,
)
