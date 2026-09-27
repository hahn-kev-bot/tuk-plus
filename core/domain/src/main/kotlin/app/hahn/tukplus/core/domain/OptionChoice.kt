package app.hahn.tukplus.core.domain

import app.hahn.tukplus.core.model.OptionGroup

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
    fun isMissing(group: OptionGroup, chosen: Set<String>): Boolean = when {
        group.required != true && chosen.isEmpty() -> false
        group.multipleConstraint == "exactly" && group.multipleN != null && !isSingle(group) -> chosen.size != group.multipleN
        group.required == true -> chosen.isEmpty()
        else -> false
    }
}
