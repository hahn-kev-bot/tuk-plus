package app.hahn.tukplus.core.model

import kotlinx.serialization.Serializable

/** Response of `workflows?commerce_delivery={commerceWorkflowId}`. */
@Serializable
data class CommerceDelivery(
    val express: DeliveryFleet? = null,
    val fallback: DeliveryFleet? = null,
)

@Serializable
data class DeliveryFleet(
    val id: String,
    val businessId: String? = null,
    val workflowTypeName: String? = null,
    val name: String? = null,
    /** "active" or "inactive". */
    val state: String? = null,
    val updatedAt: String? = null,
    val data: DeliveryFleetData? = null,
)

@Serializable
data class DeliveryFleetData(
    /** Fee by distance step, see api-reference §7. */
    val pricingArray: List<Double>? = null,
    @Serializable(LenientDoubleSerializer::class) val surge: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val remitAmount: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val peakHourMaxDistance: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val inactiveMaxDistance: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val maxDistance: Double? = null,
    val inactiveMessage: String? = null,
)

/** Response of `directions?origin=…&destination=…`. */
@Serializable
data class Directions(
    val source: String? = null,
    /** Metres. */
    @Serializable(LenientDoubleSerializer::class) val distance: Double? = null,
    val encodedPolyline: String? = null,
)

/** Response of `delivery/price_check`. */
@Serializable
data class PriceCheck(
    /** Kilometres. */
    @Serializable(LenientDoubleSerializer::class) val drivingDistance: Double? = null,
    @Serializable(LenientDoubleSerializer::class) val price: Double? = null,
)
