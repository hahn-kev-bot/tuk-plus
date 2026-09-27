package app.hahn.tukplus.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hahn.tukplus.R
import app.hahn.tukplus.core.domain.CartAddition
import app.hahn.tukplus.core.domain.CartLine
import app.hahn.tukplus.core.domain.CartOption
import app.hahn.tukplus.core.domain.CartRules
import app.hahn.tukplus.core.domain.MenuRules
import app.hahn.tukplus.core.domain.OptionChoice
import app.hahn.tukplus.core.domain.OptionStyle
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.MenuOptionGroup
import app.hahn.tukplus.core.model.parseIntLikeJavaScript
import app.hahn.tukplus.core.pricing.CartPricing
import app.hahn.tukplus.core.pricing.ItemPrice
import app.hahn.tukplus.core.pricing.ShopSettings
import app.hahn.tukplus.ui.common.Badge
import app.hahn.tukplus.ui.common.TukImage
import app.hahn.tukplus.ui.common.baht
import app.hahn.tukplus.ui.theme.PictureShapes
import app.hahn.tukplus.ui.theme.TukIcons
import kotlin.math.floor

/**
 * Item details with option groups (design: Item). The option rules are the web app's rules
 * (docs/pricing.md §15): groups with a `condition` show only when a chosen option matches,
 * required groups come first, and options of hidden groups are removed. The price comes from
 * `core:pricing`. [limit] is how many more of this item the cart can take (null: no limit).
 * "Add to cart" gives the chosen options, the quantity and the note to [onAdd].
 */
@Composable
fun ItemSheet(entry: MenuEntry, menu: Menu, shop: ShopSettings, limit: Int?, onClose: () -> Unit, onAdd: (CartAddition) -> Unit) {
    val item = entry.item
    val colors = MaterialTheme.colorScheme
    val groupIds = item.options.orEmpty()
    // Chosen options: group blob id to (option id to quantity).
    var chosen by remember(item.id) { mutableStateOf<Map<String, Map<String, Int>>>(emptyMap()) }
    var quantity by remember(item.id) { mutableIntStateOf(1) }
    var note by remember(item.id) { mutableStateOf("") }
    val maxQuantity = minOf(CartRules.MAX_QUANTITY, limit ?: CartRules.MAX_QUANTITY)

    val groups = OptionChoice.visibleGroups(groupIds, menu.optionGroups, chosen)
    val invalid = OptionChoice.invalidGroups(groupIds, menu.optionGroups, chosen)
    val options = CartRules.optionsWithQuantity(groups, chosen)
    val unit = remember(chosen, shop) { unitPrice(entry, menu, options, shop) }
    val setGroup = { groupId: String, next: Map<String, Int> ->
        chosen = OptionChoice.dropHidden(groupIds, menu.optionGroups, chosen + (groupId to next))
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(280.dp)) {
                TukImage(item.pic, Modifier.fillMaxWidth().height(280.dp), shape = PictureShapes.Arch)
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = colors.surfaceContainer),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(44.dp),
                ) { Icon(TukIcons.Close, contentDescription = stringResource(R.string.close)) }
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.displayName, style = MaterialTheme.typography.headlineMedium)
                item.displayDescription?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                MenuRules.discountedPrice(item)?.let { Text(baht(it), style = MaterialTheme.typography.titleLarge) }
            }
            for (group in groups) {
                OptionGroupCard(group, chosen[group.blobId].orEmpty()) { setGroup(group.blobId, it) }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.item_note)) },
                placeholder = { Text(stringResource(R.string.item_note_hint)) },
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        Surface(color = colors.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHighest) {
                    Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (quantity > 1) quantity-- }, modifier = Modifier.size(52.dp, 56.dp)) {
                            Icon(TukIcons.Minus, contentDescription = stringResource(R.string.item_less))
                        }
                        Text(quantity.toString(), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                        IconButton(onClick = { if (quantity < maxQuantity) quantity++ }, modifier = Modifier.size(52.dp, 56.dp)) {
                            Icon(TukIcons.Plus, contentDescription = stringResource(R.string.item_more))
                        }
                    }
                }
                Button(
                    onClick = { onAdd(CartAddition(entry, options, quantity.coerceAtMost(maxQuantity), note)) },
                    enabled = invalid.isEmpty() && item.outOfStock != true && maxQuantity > 0,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(
                        if (maxQuantity > 0) stringResource(R.string.item_add, baht(unit * quantity)) else stringResource(R.string.item_limit_reached),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** The unit price of the item with [options], with the web price code (docs/pricing.md §2–§4). */
private fun unitPrice(entry: MenuEntry, menu: Menu, options: List<CartOption>, shop: ShopSettings): Int {
    val ids = options.map { it.groupId }.toSet()
    val line = CartLine(
        lineId = "sheet",
        itemId = entry.item.id,
        quantity = 1,
        options = options,
        item = entry.raw,
        groups = menu.optionGroups.filterKeys { it in ids }.mapValues { it.value.raw },
    )
    val price = ItemPrice.lineUnit(CartPricing.webItem(line, shop))
    return if (price.isNaN()) 0 else floor(price + 0.5).toInt()
}

@Composable
private fun OptionGroupCard(group: MenuOptionGroup, chosen: Map<String, Int>, onChange: (Map<String, Int>) -> Unit) {
    val g = group.group
    val colors = MaterialTheme.colorScheme
    val single = OptionChoice.isSingle(g)
    val style = OptionChoice.style(g)
    val radio = style != OptionStyle.CHECKBOX
    val withQuantity = OptionChoice.allowsQuantity(g)
    val chosenIds = chosen.keys
    // A tap chooses or removes an option; a new option starts with quantity 1.
    val tap = { optionId: String ->
        val ids = OptionChoice.tap(g, chosenIds, optionId)
        onChange(ids.associateWith { chosen[it] ?: 1 })
    }
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (g.required == true) {
                    Badge(stringResource(R.string.option_badge_required), colors.primary, colors.onPrimary)
                } else {
                    Badge(stringResource(R.string.option_badge_optional), colors.surfaceContainerHighest, colors.onSurface)
                }
            }
            Text(
                when {
                    g.multipleConstraint == "exactly" && g.multipleN != null && !single -> stringResource(R.string.option_exactly, g.multipleN!!)
                    g.multipleConstraint == "up_to" && g.multipleN != null && !single -> stringResource(R.string.option_up_to, g.multipleN!!)
                    single -> stringResource(R.string.option_one)
                    else -> stringResource(R.string.option_any)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (style == OptionStyle.RADIO_WITH_NONE) {
                // Only one option can be active, and the group is optional: "None" removes the choice.
                Row(
                    Modifier.selectable(selected = chosen.isEmpty(), role = Role.RadioButton) { onChange(OptionChoice.tapNone(g, chosenIds).associateWith { 1 }) }
                        .fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.option_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(selected = chosen.isEmpty(), onClick = null)
                }
                HorizontalDivider(color = colors.surfaceContainerHighest)
            }
            g.items.forEachIndexed { index, option ->
                val soldOut = option.outOfStock == true
                val selected = option.id in chosenIds
                val price = option.price?.let(::parseIntLikeJavaScript) ?: 0
                val priceText = if (price == 0) stringResource(R.string.option_free) else "+" + baht(price)
                val enabled = !soldOut && OptionChoice.canAdd(g, chosenIds, option.id)
                val rowModifier = if (radio) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { tap(option.id) }
                } else {
                    Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { tap(option.id) }
                }
                Row(rowModifier.fillMaxWidth().height(52.dp).alpha(if (soldOut) 0.5f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(option.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (soldOut) stringResource(R.string.sold_out) else priceText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    if (withQuantity && selected) {
                        OptionQuantity(chosen[option.id] ?: 1, onMinus = { onChange(OptionChoice.changeQuantity(g, chosen, option.id, -1)) }, onPlus = { onChange(OptionChoice.changeQuantity(g, chosen, option.id, 1)) })
                    } else if (radio) {
                        RadioButton(selected = selected, onClick = null)
                    } else {
                        Checkbox(checked = selected, onCheckedChange = null)
                    }
                }
                if (index < g.items.lastIndex) HorizontalDivider(color = colors.surfaceContainerHighest)
            }
        }
    }
}

/** − quantity + for one option of a group with `allow_multiple`. At 0 the option is removed. */
@Composable
private fun OptionQuantity(quantity: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus, modifier = Modifier.size(40.dp)) { Icon(TukIcons.Minus, contentDescription = stringResource(R.string.item_less), modifier = Modifier.size(16.dp)) }
        Text(quantity.toString(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
        IconButton(onClick = onPlus, modifier = Modifier.size(40.dp)) { Icon(TukIcons.Plus, contentDescription = stringResource(R.string.item_more), modifier = Modifier.size(16.dp)) }
    }
}
