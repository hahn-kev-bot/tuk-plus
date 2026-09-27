package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.MenuItem
import app.hahn.tukplus.core.model.parseIntLikeJavaScript
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.ZonedDateTime
import kotlin.math.floor

/** One part of the menu screen: a category title and its items. */
data class MenuSection(val title: String, val entries: List<MenuEntry>)

/** Menu display rules of the web shop page (api-reference §6.2). */
object MenuRules {
    /** Title of the last section, for items with no known category. */
    const val OTHER_SECTION = "Other"

    /** The web app hides items below this price when they have no options and no free gift. */
    private const val MIN_PLAIN_PRICE = 9
    private val HOUR_TAG = Regex("""\*(\d{1,2})-(\d{1,2})""")

    /**
     * Gives the items to show at [now] (Chiang Mai time). An item is removed when:
     *
     * - it is hidden,
     * - its `schedule` is a list of days that does not include today
     *   (only the first 3 letters of a day are used, so "monday" is "mon"),
     * - it has tags such as "*11-15" and the current hour is in none of them
     *   (open when 11 ≤ hour < 15; "*22-2" goes past midnight),
     * - its price is less than 9, it has no options and it has no free gift.
     *
     * The order stays the same, but items that are out of stock move to the end of their category.
     */
    fun visibleEntries(menu: Menu, now: ZonedDateTime): List<MenuEntry> {
        val local = now.withZoneSameInstant(ChiangMaiTime.ZONE)
        val today = OpenHours.dayKey(local.dayOfWeek)
        val visible = menu.entries.filter { entry ->
            val item = entry.item
            item.hidden != true &&
                isScheduledOn(item.schedule, today) &&
                isInHourTags(item.tags.orEmpty(), local.hour) &&
                !isHiddenCheapItem(item)
        }
        return moveOutOfStockToEnd(visible)
    }

    /** True when [schedule] is not a usable list of days, or when the list includes [today]. */
    private fun isScheduledOn(schedule: JsonElement?, today: String): Boolean {
        val days = when (schedule) {
            is JsonArray -> schedule.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            is JsonPrimitive -> if (schedule.isString) schedule.content.split(',') else return true
            else -> return true
        }.map { it.trim().lowercase().take(3) }.filter { it.isNotEmpty() }
        return days.isEmpty() || today in days
    }

    /** True when there is no hour tag, or when [hour] is in one or more of the hour tags. */
    private fun isInHourTags(tags: List<String>, hour: Int): Boolean {
        val windows = tags.mapNotNull { HOUR_TAG.matchEntire(it.trim()) }
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
        if (windows.isEmpty()) return true
        return windows.any { (start, end) ->
            when {
                start == end -> true
                end < start -> hour >= start || hour < end
                else -> hour in start until end
            }
        }
    }

    private fun isHiddenCheapItem(item: MenuItem): Boolean {
        val price = basePrice(item) ?: return false // Like JavaScript: NaN < 9 is false.
        val noGift = item.freeGift == null || item.freeGift is JsonNull
        return price < MIN_PLAIN_PRICE && item.options.isNullOrEmpty() && noGift
    }

    /** Puts out-of-stock items after the other items of the same category. Each category keeps its places in the list. */
    private fun moveOutOfStockToEnd(entries: List<MenuEntry>): List<MenuEntry> {
        val result = entries.toMutableList()
        entries.indices.groupBy { entries[it].item.category }.values.forEach { places ->
            val (inStock, outOfStock) = places.map { entries[it] }.partition { it.item.outOfStock != true }
            (inStock + outOfStock).forEachIndexed { i, entry -> result[places[i]] = entry }
        }
        return result
    }

    /**
     * Puts [entries] into sections in the order of `menu.categories`. An item goes to the
     * category with the same name. The title is the English name, else the name. Items with
     * no category, or with a category that is not in the list, go to a last section "Other".
     * Empty sections are removed.
     */
    fun sections(menu: Menu, entries: List<MenuEntry>): List<MenuSection> {
        val categories = menu.categories.distinctBy { it.name }
        val known = categories.map { it.name }.toSet()
        val byCategory = entries.groupBy { it.item.category?.takeIf { name -> name in known } }
        val sections = categories.mapNotNull { category ->
            val items = byCategory[category.name].orEmpty()
            if (items.isEmpty()) null
            else MenuSection(category.en?.takeIf { it.isNotBlank() } ?: category.name, items)
        }
        val other = byCategory[null].orEmpty()
        return if (other.isEmpty()) sections else sections + MenuSection(OTHER_SECTION, other)
    }

    /** The price before discount: `parseInt(price)` like the web app. Null when the price is not a number. */
    fun basePrice(item: MenuItem): Int? = item.price?.let(::parseIntLikeJavaScript)

    /**
     * The price after discount. Null when the price is not a number.
     *
     * The discount is read with `parseInt`. With no discount (null or 0) the result is the base price.
     * With `discount_type` "number", the discount is an amount in baht. Else it is a percent, and the
     * result is rounded like JavaScript `Math.round` (a half goes up: 40.5 → 41). The result is never
     * less than 0.
     */
    fun discountedPrice(item: MenuItem): Int? {
        val base = basePrice(item) ?: return null
        val discount = item.discount?.let(::parseIntLikeJavaScript)
        if (discount == null || discount == 0) return base
        return if (item.discountType == "number") {
            maxOf(0, base - discount)
        } else {
            maxOf(0, floor(base * (1 - discount / 100.0) + 0.5).toInt())
        }
    }

    /**
     * True when [text] is in the name, the English name, the description or the category of the
     * item. Case is ignored. Blank text matches all items.
     */
    fun matches(entry: MenuEntry, text: String): Boolean {
        val needle = text.trim()
        if (needle.isEmpty()) return true
        val item = entry.item
        return listOf(item.name, item.en?.name, item.description, item.en?.description, item.category)
            .any { it?.contains(needle, ignoreCase = true) == true }
    }
}
