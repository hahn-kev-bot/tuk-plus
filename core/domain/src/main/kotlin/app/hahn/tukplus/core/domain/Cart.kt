package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.MenuItem
import app.hahn.tukplus.core.model.MenuOptionGroup
import app.hahn.tukplus.core.model.OptionGroup
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.parseIntLikeJavaScript
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON settings for the saved cart file. Unknown keys are ignored, so an older app can read a newer file. */
val CartStoreJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** One chosen option of a cart line. [quantity] is more than 1 only for groups with `allow_multiple`. */
@Serializable
data class CartOption(
    /** The `options_menu` blob id. */
    val groupId: String,
    val optionId: String,
    val quantity: Int = 1,
)

/**
 * One line of the cart. The line keeps a copy of the menu data from the time when the
 * user added it ([item], [groups]), so that the cart works without the menu and the app
 * can find changes after a menu refresh (PLAN.md §5.7).
 */
@Serializable
data class CartLine(
    /** Unique in the cart. */
    val lineId: String,
    val itemId: String,
    val quantity: Int,
    val options: List<CartOption> = emptyList(),
    val note: String = "",
    /** The raw menu item (`digital_menu` entry). */
    val item: JsonObject,
    /** The raw option groups that the chosen options come from, by blob id. */
    val groups: Map<String, JsonObject> = emptyMap(),
    /** `workflow.updated_at` of the menu that [item] comes from. */
    val menuVersion: String? = null,
) {
    val hasOptions: Boolean get() = options.isNotEmpty()
}

/** The one cart of the device. It is for one shop (one Commerce workflow). */
@Serializable
data class Cart(
    val businessId: String,
    val workflowId: String,
    val shopName: String = "",
    /** Epoch milliseconds. The web app's `basket.created_at`. */
    val createdAt: Long,
    /** Epoch milliseconds of the last change. */
    val updatedAt: Long = createdAt,
    /** The order note (`basket.notes`). */
    val notes: String = "",
    /** "delivery", "take-away" or "dine-in" (the web app's fulfilment values). Null: not chosen yet. */
    val fulfilment: String? = null,
    val lines: List<CartLine> = emptyList(),
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** What the user wants to add: an item with its options, a quantity and a note. */
data class CartAddition(
    val entry: MenuEntry,
    val options: List<CartOption>,
    val quantity: Int,
    val note: String = "",
)

/** The result of [CartRules.add]. */
sealed interface AddResult {
    data class Added(val cart: Cart) : AddResult

    /** The cart is for another shop and is not old. The app asks "Start a new cart?". */
    data class OtherShop(val current: Cart) : AddResult
}

/** Why a cart line cannot be ordered. */
sealed interface Blocked {
    data object SoldOut : Blocked
    data class Option(val name: String) : Blocked
}

/** A change that a menu refresh made to a cart line. */
sealed interface LineChange {
    val lineId: String

    /** The item is no longer on the menu. The line is removed. */
    data class Removed(override val lineId: String, val name: String) : LineChange

    /** The item is sold out or hidden. The line stays, but the cart cannot go to checkout. */
    data class SoldOut(override val lineId: String, val name: String) : LineChange

    /** A chosen option is gone or sold out. The line stays, but the cart cannot go to checkout. */
    data class OptionUnavailable(override val lineId: String, val name: String, val option: String) : LineChange

    /** The unit price changed. The line now has the new menu data. */
    data class PriceChanged(override val lineId: String, val name: String, val oldPrice: Int, val newPrice: Int) : LineChange
}

data class Reconciled(val cart: Cart, val changes: List<LineChange>) {
    /** Lines that cannot be ordered now (sold out or with an option that is not available). */
    val blockedLineIds: Set<String>
        get() = changes.filter { it is LineChange.SoldOut || it is LineChange.OptionUnavailable }.map { it.lineId }.toSet()
}

/** Cart rules of the web app (api-reference §7a, PLAN.md §5.7). The cart is on the device only. */
object CartRules {
    /** A cart for another shop that is older than this is replaced without a question. */
    const val REPLACE_AFTER_MS: Long = 60 * 60 * 1000L

    /** The largest quantity of one line. */
    const val MAX_QUANTITY = 99

    /**
     * Adds [addition] to [cart] (null when there is no cart).
     *
     * - A cart for another shop is replaced when it is older than 60 minutes. Otherwise the
     *   result is [AddResult.OtherShop], and the app asks the user (then [startNew]).
     * - An item without options and without a note merges with an equal line.
     *   An item with options is always a new line.
     */
    fun add(
        cart: Cart?,
        businessId: String,
        shopName: String,
        menu: Menu,
        addition: CartAddition,
        now: Long,
        newLineId: () -> String,
    ): AddResult {
        val base = when {
            cart == null || cart.isEmpty -> null
            cart.businessId == businessId && cart.workflowId == menu.workflowId -> cart
            now - cart.createdAt > REPLACE_AFTER_MS -> null
            else -> return AddResult.OtherShop(cart)
        }
        val start = base ?: Cart(businessId, menu.workflowId, shopName, createdAt = now, notes = cart?.takeIf { it.businessId == businessId }?.notes.orEmpty())
        val limit = addLimit(start, addition.entry.item)
        if (limit != null && limit <= 0) return AddResult.Added(start)
        val quantity = addition.quantity.coerceIn(1, MAX_QUANTITY).let { if (limit != null) minOf(it, limit) else it }
        val note = addition.note.trim()
        val itemId = addition.entry.item.id
        val merge = if (addition.options.isEmpty() && note.isEmpty()) {
            start.lines.firstOrNull { it.itemId == itemId && !it.hasOptions && it.note.isEmpty() }
        } else {
            null
        }
        val lines = if (merge != null) {
            start.lines.map { if (it.lineId == merge.lineId) it.copy(quantity = (it.quantity + quantity).coerceAtMost(MAX_QUANTITY)) else it }
        } else {
            val groupIds = addition.options.map { it.groupId }.toSet()
            start.lines + CartLine(
                lineId = newLineId(),
                itemId = itemId,
                quantity = quantity,
                options = addition.options,
                note = note,
                item = addition.entry.raw,
                groups = menu.optionGroups.filterKeys { it in groupIds }.mapValues { it.value.raw },
                menuVersion = menu.version,
            )
        }
        return AddResult.Added(start.copy(shopName = shopName.ifEmpty { start.shopName }, updatedAt = now, lines = lines))
    }

    /**
     * How many more of [item] the cart can take, or null for no limit (web `ShopMenuOptions.submit`):
     * `max_count` limits the quantity of the item in all lines, and a free gift can be in the
     * cart once. [add] uses the limit; the item sheet should use it to limit the quantity.
     */
    fun addLimit(cart: Cart?, item: MenuItem): Int? {
        val inCart = cart?.lines?.filter { it.itemId == item.id }?.sumOf { it.quantity } ?: 0
        val limits = listOfNotNull(
            item.maxCount?.takeIf { it != 0 }?.let { it - inCart },
            if (isTruthy(item.freeGift)) 1 - inCart else null,
        )
        return limits.minOrNull()
    }

    private fun isTruthy(value: JsonElement?): Boolean = when (value) {
        null, JsonNull -> false
        is JsonPrimitive -> if (value.isString) value.content.isNotEmpty() else value.content !in setOf("false", "0", "0.0")
        else -> true
    }

    /** A new cart with only [addition] (after the user said yes to "Start a new cart?"). */
    fun startNew(businessId: String, shopName: String, menu: Menu, addition: CartAddition, now: Long, newLineId: () -> String): Cart =
        (add(null, businessId, shopName, menu, addition, now, newLineId) as AddResult.Added).cart

    /** Sets the quantity of a line. A quantity of 0 or less removes the line. */
    fun setQuantity(cart: Cart, lineId: String, quantity: Int, now: Long): Cart {
        val lines = if (quantity <= 0) {
            cart.lines.filter { it.lineId != lineId }
        } else {
            cart.lines.map { if (it.lineId == lineId) it.copy(quantity = quantity.coerceAtMost(MAX_QUANTITY)) else it }
        }
        return cart.copy(lines = lines, updatedAt = now)
    }

    fun remove(cart: Cart, lineId: String, now: Long): Cart = setQuantity(cart, lineId, 0, now)

    fun setNotes(cart: Cart, notes: String, now: Long): Cart = cart.copy(notes = notes, updatedAt = now)

    fun setFulfilment(cart: Cart, fulfilment: String, now: Long): Cart = cart.copy(fulfilment = fulfilment, updatedAt = now)

    /**
     * The order type to use: the user's choice if the shop offers it, else the first
     * type that the shop offers (the web app shows them in this order). Null when the shop
     * gives no list; the web app then offers delivery only.
     */
    fun fulfilment(cart: Cart, offered: List<String>?): String {
        val options = offered.orEmpty().filter { it in FULFILMENT_ORDER }.sortedBy { FULFILMENT_ORDER.indexOf(it) }
        return cart.fulfilment?.takeIf { it in options } ?: options.firstOrNull() ?: FULFILMENT_ORDER.first()
    }

    /** The order types in the order of the cart screen. */
    val FULFILMENT_ORDER = listOf("delivery", "take-away", "dine-in")

    /**
     * Why [line] cannot be ordered now, from its own copy of the menu data, or null when it can.
     * [reconcile] keeps the copy up to date.
     */
    fun blockedReason(line: CartLine): Blocked? {
        val item = LinePrice.menuItem(line) ?: return Blocked.SoldOut
        if (item.outOfStock == true || item.hidden == true) return Blocked.SoldOut
        val missing = line.options.firstOrNull { chosen ->
            val option = LinePrice.group(line, chosen.groupId)?.items?.firstOrNull { it.id == chosen.optionId }
            option == null || option.outOfStock == true || chosen.groupId !in item.options.orEmpty()
        }
        return missing?.let { Blocked.Option(LinePrice.optionName(line, it)) }
    }

    /**
     * Compares each line with the current [menu] and gives the updated cart and the changes.
     * Lines of removed items are removed. Other lines get the new menu data, so that the
     * prices in the cart are the prices that the order will use.
     */
    fun reconcile(cart: Cart, menu: Menu): Reconciled {
        if (menu.workflowId != cart.workflowId) return Reconciled(cart, emptyList())
        val entries = menu.entries.associateBy { it.item.id }
        val changes = mutableListOf<LineChange>()
        val lines = cart.lines.mapNotNull { line ->
            val oldName = LinePrice.name(line)
            val entry = entries[line.itemId]
            if (entry == null) {
                changes += LineChange.Removed(line.lineId, oldName)
                return@mapNotNull null
            }
            val item = entry.item
            if (item.outOfStock == true || item.hidden == true) changes += LineChange.SoldOut(line.lineId, item.displayName)
            val missing = line.options.firstOrNull { chosen ->
                val option = menu.optionGroups[chosen.groupId]?.group?.items?.firstOrNull { it.id == chosen.optionId }
                option == null || option.outOfStock == true || chosen.groupId !in item.options.orEmpty()
            }
            if (missing != null) {
                val name = LinePrice.optionName(line, missing)
                changes += LineChange.OptionUnavailable(line.lineId, item.displayName, name)
            }
            val updated = line.copy(
                item = entry.raw,
                groups = line.groups.keys.associateWith { id -> menu.optionGroups[id]?.raw ?: line.groups.getValue(id) },
                menuVersion = menu.version,
            )
            val oldPrice = LinePrice.previewUnit(line)
            val newPrice = LinePrice.previewUnit(updated)
            if (oldPrice != newPrice) changes += LineChange.PriceChanged(line.lineId, item.displayName, oldPrice, newPrice)
            updated
        }
        return Reconciled(cart.copy(lines = lines), changes)
    }

    /** The chosen options of the item sheet as cart options, in menu order. */
    fun options(groups: List<MenuOptionGroup>, chosen: Map<String, Set<String>>): List<CartOption> =
        groups.flatMap { group ->
            val ids = chosen[group.blobId].orEmpty()
            group.group.items.filter { it.id in ids }.map { CartOption(group.blobId, it.id) }
        }
}

/** Reads names and a price preview from the raw copies in a [CartLine]. */
object LinePrice {
    fun menuItem(line: CartLine): MenuItem? =
        runCatching { TukJson.decodeFromJsonElement(MenuItem.serializer(), line.item) }.getOrNull()

    fun group(line: CartLine, groupId: String): OptionGroup? =
        line.groups[groupId]?.let { raw -> runCatching { TukJson.decodeFromJsonElement(OptionGroup.serializer(), raw) }.getOrNull() }

    fun name(line: CartLine): String = menuItem(line)?.displayName ?: line.itemId

    fun optionName(line: CartLine, option: CartOption): String =
        group(line, option.groupId)?.items?.firstOrNull { it.id == option.optionId }?.displayName ?: option.optionId

    /** The option names for the cart screen, for example "Large", "2× Egg". */
    fun optionNames(line: CartLine): List<String> = line.options.map { chosen ->
        val name = optionName(line, chosen)
        if (chosen.quantity > 1) "${chosen.quantity}× $name" else name
    }

    /**
     * A unit price to find price changes: the discounted item price plus the option prices.
     * The amounts of the order come from `core:pricing`, not from this function.
     */
    fun previewUnit(line: CartLine): Int {
        val base = menuItem(line)?.let(MenuRules::discountedPrice) ?: 0
        val options = line.options.sumOf { chosen ->
            val option = group(line, chosen.groupId)?.items?.firstOrNull { it.id == chosen.optionId }
            (option?.price?.let(::parseIntLikeJavaScript) ?: 0) * chosen.quantity
        }
        return base + options
    }
}
