package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.MenuOptionGroup
import app.hahn.tukplus.core.model.OptionGroup
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** How an option group looks in the item sheet. */
enum class OptionStyle {
    /** Required, choose one: radio buttons. The choice cannot become empty. */
    RADIO,
    /** Optional, choose one, two or more options: radio buttons with a "None" row first. */
    RADIO_WITH_NONE,
    /** Choose several, or an optional group with only one option: check boxes. */
    CHECKBOX,
}

/** What happens when the user taps an option in an option group (item sheet). */
object OptionChoice {

    fun style(group: OptionGroup): OptionStyle = when {
        !isSingle(group) -> OptionStyle.CHECKBOX
        group.required == true -> OptionStyle.RADIO
        group.items.size <= 1 -> OptionStyle.CHECKBOX
        else -> OptionStyle.RADIO_WITH_NONE
    }

    /** True when the group allows one option at most ("select": "single" or no value). */
    fun isSingle(group: OptionGroup): Boolean = group.select != "multiple"

    /** The largest number of options that the user can choose, or null for no limit. */
    fun limit(group: OptionGroup): Int? = when {
        isSingle(group) -> 1
        group.multipleConstraint == "up_to" || group.multipleConstraint == "exactly" -> group.multipleN
        else -> null
    }

    /**
     * The new choice after a tap on [optionId].
     *
     * - With check boxes ([OptionStyle.CHECKBOX]), a tap on a chosen option removes it.
     * - With radio buttons, a tap on the chosen option changes nothing. In
     *   [OptionStyle.RADIO_WITH_NONE] the user taps "None" ([tapNone]) to remove the choice.
     * - In a single-choice group, a tap on another option replaces the choice.
     * - In a multiple-choice group, a tap adds the option if the limit allows it.
     * - Sold-out options and unknown ids do not change the choice.
     */
    fun tap(group: OptionGroup, chosen: Set<String>, optionId: String): Set<String> {
        val option = group.items.firstOrNull { it.id == optionId } ?: return chosen
        if (optionId in chosen) {
            return if (style(group) == OptionStyle.CHECKBOX) chosen - optionId else chosen
        }
        if (option.outOfStock == true) return chosen
        if (isSingle(group)) return setOf(optionId)
        val max = limit(group)
        return if (max != null && chosen.size >= max) chosen else chosen + optionId
    }

    /** The choice after a tap on the "None" row of an optional single-choice group. */
    fun tapNone(group: OptionGroup, chosen: Set<String>): Set<String> =
        if (style(group) == OptionStyle.RADIO_WITH_NONE) emptySet() else chosen

    /** True when the user can still add [optionId] (the limit is not reached). */
    fun canAdd(group: OptionGroup, chosen: Set<String>, optionId: String): Boolean {
        if (optionId in chosen) return true
        if (isSingle(group)) return true
        val max = limit(group) ?: return true
        return chosen.size < max
    }

    /** True when the group needs more choices before the item can go into the cart. */
    fun isMissing(group: OptionGroup, chosen: Set<String>): Boolean = isMissing(group, chosen.associateWith { 1 })

    /**
     * The web rule at "Add to basket" (`checkRequirements`, docs/pricing.md §15), with a
     * quantity per option ([quantities], option id to quantity):
     *
     * - a required group needs at least one option;
     * - a "multiple" group with "exactly" needs a total quantity of exactly `multiple_n`,
     *   also when the group is optional (the web app checks it then too). Without
     *   `multiple_n` it is never valid;
     * - a "multiple" group with "up_to" can have a total quantity up to `multiple_n`.
     */
    fun isMissing(group: OptionGroup, quantities: Map<String, Int>): Boolean {
        val chosen = quantities.filterValues { it > 0 }
        if (group.required == true && chosen.isEmpty()) return true
        if (isSingle(group)) return false
        val total = chosen.values.sum()
        val n = group.multipleN
        return when (group.multipleConstraint) {
            "exactly" -> n == null || total != n
            "up_to" -> n != null && total > n
            else -> false
        }
    }

    /**
     * Changes the quantity of a chosen option by [delta] in a group with `allow_multiple`.
     * The total quantity of the group stays within [limit]. At 0 the option is removed.
     */
    fun changeQuantity(group: OptionGroup, chosen: Map<String, Int>, optionId: String, delta: Int): Map<String, Int> {
        val current = chosen[optionId] ?: 0
        if (delta > 0) {
            val option = group.items.firstOrNull { it.id == optionId } ?: return chosen
            if (option.outOfStock == true) return chosen
            val max = limit(group)
            if (max != null && chosen.values.sum() + delta > max) return chosen
        }
        val next = current + delta
        return if (next <= 0) chosen - optionId else chosen + (optionId to next)
    }

    /** True when the options of [group] can have a quantity each (a "multiple" group with `allow_multiple`). */
    fun allowsQuantity(group: OptionGroup): Boolean = !isSingle(group) && group.allowMultiple == true

    /**
     * The groups that the item sheet shows, in the web order (docs/pricing.md §15): the item's
     * group ids in order, only groups that exist and have options, only groups whose
     * `condition` is met, and then required groups first.
     *
     * A condition is met when the JSON text of a chosen option (lower case, with its
     * `quantity`) contains the condition text. The condition text is not changed to lower case.
     * [chosen] is group id to (option id to quantity).
     */
    fun visibleGroups(
        itemGroupIds: List<String>,
        groups: Map<String, MenuOptionGroup>,
        chosen: Map<String, Map<String, Int>>,
    ): List<MenuOptionGroup> {
        val chosenJson = chosen.flatMap { (groupId, options) ->
            val group = groups[groupId] ?: return@flatMap emptyList()
            options.filterValues { it > 0 }.mapNotNull { (optionId, quantity) -> optionJson(group, optionId, quantity) }
        }
        val shown = itemGroupIds.mapNotNull { groups[it] }.filter { group ->
            if (group.group.items.isEmpty()) return@filter false
            val condition = group.group.condition
            condition.isNullOrEmpty() || chosenJson.any { it.contains(condition) }
        }
        return shown.sortedBy { if (it.group.required == true) 0 else 1 }
    }

    /** [chosen] without the options of groups that are not shown (the web app removes them). */
    fun dropHidden(
        itemGroupIds: List<String>,
        groups: Map<String, MenuOptionGroup>,
        chosen: Map<String, Map<String, Int>>,
    ): Map<String, Map<String, Int>> {
        var current = chosen
        while (true) {
            val visible = visibleGroups(itemGroupIds, groups, current).map { it.blobId }.toSet()
            val next = current.filterKeys { it in visible }
            if (next == current) return current
            current = next
        }
    }

    /** The groups (blob ids) that stop "Add to basket", in display order. */
    fun invalidGroups(
        itemGroupIds: List<String>,
        groups: Map<String, MenuOptionGroup>,
        chosen: Map<String, Map<String, Int>>,
    ): List<String> = visibleGroups(itemGroupIds, groups, chosen)
        .filter { isMissing(it.group, chosen[it.blobId].orEmpty()) }
        .map { it.blobId }

    private fun optionJson(group: MenuOptionGroup, optionId: String, quantity: Int): String? {
        val raw = (group.raw["items"] as? JsonArray)?.firstOrNull { (it as? JsonObject)?.get("id")?.let { id -> id is JsonPrimitive && id.content == optionId } == true } as? JsonObject
            ?: return null
        return JsonObject(raw + ("quantity" to JsonPrimitive(quantity))).toString().lowercase()
    }
}
