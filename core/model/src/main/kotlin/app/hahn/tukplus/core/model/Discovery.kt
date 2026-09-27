package app.hahn.tukplus.core.model

import kotlinx.serialization.Serializable

/** Item of `recommendations/foryou`. [key] is a business id. */
@Serializable
data class ForYouScore(
    val key: String,
    @Serializable(LenientDoubleSerializer::class) val score: Double? = null,
)

/** Item of `businesses?type=new_shops`. */
@Serializable
data class NewShop(
    val id: String,
    val name: String? = null,
    val profilePicUrl: String? = null,
    val productPicUrl: String? = null,
)

/** Response of `autocomplete?business=`. */
@Serializable
data class BusinessAutocomplete(
    val businesses: List<AutocompleteEntry>? = null,
)

@Serializable
data class AutocompleteEntry(
    /** Shop name. */
    val key: String = "",
    /** Business id. */
    val value: String = "",
    val pic: String? = null,
    @Serializable(LenientBooleanSerializer::class) val hidden: Boolean? = null,
)

/** Item of `search/menu_items?text=`: the number of matching menu items in one shop. */
@Serializable
data class MenuItemSearchHit(
    val businessName: String? = null,
    val businessId: String,
    val businessPic: String? = null,
    @Serializable(LenientIntSerializer::class) val count: Int? = null,
)

/** Response of `short_link/{handle}`. [url] is for example "/shop/<businessId>". */
@Serializable
data class ShortLink(
    val id: String? = null,
    val shortId: String? = null,
    val url: String? = null,
    val state: String? = null,
) {
    /** The business id when [url] is a shop link. */
    val businessId: String?
        get() = url?.substringAfter("/shop/", missingDelimiterValue = "")?.substringBefore('?')
            ?.substringBefore('/')?.takeIf { it.isNotBlank() }
}

/** Result of `helpers/shop_open`. The API sends plain text: "yes", or a reason. */
sealed interface ShopOpenStatus {
    data object Open : ShopOpenStatus
    data class Closed(val reason: String) : ShopOpenStatus

    companion object {
        /** Start of the reason text when a shop is closed. */
        const val CLOSED_PREFIX = "checkCommerceAllowTransaction:"

        fun fromText(text: String): ShopOpenStatus {
            val trimmed = text.trim().removeSurrounding("\"")
            return if (trimmed.equals("yes", ignoreCase = true)) Open
            else Closed(trimmed.removePrefix(CLOSED_PREFIX).trim())
        }
    }
}
