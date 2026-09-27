package app.hahn.tukplus.ui.shop

import android.widget.Toast
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.hahn.tukplus.R
import app.hahn.tukplus.core.domain.MenuRules
import app.hahn.tukplus.core.domain.OptionChoice
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.MenuOptionGroup
import app.hahn.tukplus.core.model.parseIntLikeJavaScript
import app.hahn.tukplus.ui.common.Badge
import app.hahn.tukplus.ui.common.TukImage
import app.hahn.tukplus.ui.common.baht
import app.hahn.tukplus.ui.theme.PictureShapes
import app.hahn.tukplus.ui.theme.TukIcons

/**
 * Item details with option groups (design: Item). The user can already choose options
 * and a quantity. The cart comes in phase 2, so "Add to cart" only says that for now.
 * The total here is a preview; the exact price rules (discounts on options, VAT) come
 * with `core/pricing` in phase 2.
 */
@Composable
fun ItemSheet(entry: MenuEntry, menu: Menu, onClose: () -> Unit) {
    val item = entry.item
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val groups = menu.groupsFor(item)
    // Selected option ids per option group (blob id).
    val chosen = remember(item.id) { mutableStateMapOf<String, Set<String>>() }
    var quantity by remember(item.id) { mutableIntStateOf(1) }
    var note by remember(item.id) { mutableStateOf("") }

    val unit = (MenuRules.discountedPrice(item) ?: 0) + groups.sumOf { group ->
        group.group.items.filter { it.id in chosen[group.blobId].orEmpty() }.sumOf { it.price?.let(::parseIntLikeJavaScript) ?: 0 }
    }
    val missingRequired = groups.any { OptionChoice.isMissing(it.group, chosen[it.blobId].orEmpty()) }
    val addToCartLater = stringResource(R.string.ordering_later)

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
                OptionGroupCard(group, chosen[group.blobId].orEmpty()) { chosen[group.blobId] = it }
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
                        IconButton(onClick = { quantity++ }, modifier = Modifier.size(52.dp, 56.dp)) {
                            Icon(TukIcons.Plus, contentDescription = stringResource(R.string.item_more))
                        }
                    }
                }
                Button(
                    onClick = { Toast.makeText(context, addToCartLater, Toast.LENGTH_SHORT).show() },
                    enabled = !missingRequired && item.outOfStock != true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.item_add, baht(unit * quantity)), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun OptionGroupCard(group: MenuOptionGroup, chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    val g = group.group
    val colors = MaterialTheme.colorScheme
    val single = OptionChoice.isSingle(g)
    // A required single choice looks like radio buttons. An optional one can be removed
    // again, so it looks like check boxes (the owner could not unselect "Extra cheese").
    val radio = single && g.required == true
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
                    g.multipleConstraint == "exactly" && g.multipleN != null -> stringResource(R.string.option_exactly, g.multipleN!!)
                    g.multipleConstraint == "up_to" && g.multipleN != null -> stringResource(R.string.option_up_to, g.multipleN!!)
                    single -> stringResource(R.string.option_one)
                    else -> stringResource(R.string.option_any)
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            g.items.forEachIndexed { index, option ->
                val soldOut = option.outOfStock == true
                val selected = option.id in chosen
                val price = option.price?.let(::parseIntLikeJavaScript) ?: 0
                val priceText = if (price == 0) stringResource(R.string.option_free) else "+" + baht(price)
                val enabled = !soldOut && OptionChoice.canAdd(g, chosen, option.id)
                val rowModifier = if (radio) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton) { onChange(OptionChoice.tap(g, chosen, option.id)) }
                } else {
                    Modifier.toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onChange(OptionChoice.tap(g, chosen, option.id)) }
                }
                Row(rowModifier.fillMaxWidth().height(52.dp).alpha(if (soldOut) 0.5f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(option.displayName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(if (soldOut) stringResource(R.string.sold_out) else priceText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    if (radio) RadioButton(selected = selected, onClick = null) else Checkbox(checked = selected, onCheckedChange = null)
                }
                if (index < g.items.lastIndex) HorizontalDivider(color = colors.surfaceContainerHighest)
            }
        }
    }
}
