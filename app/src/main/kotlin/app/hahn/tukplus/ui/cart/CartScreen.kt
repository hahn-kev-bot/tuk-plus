package app.hahn.tukplus.ui.cart

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hahn.tukplus.R
import app.hahn.tukplus.core.domain.Blocked
import app.hahn.tukplus.core.domain.CartLine
import app.hahn.tukplus.core.domain.CartRules
import app.hahn.tukplus.core.domain.LineChange
import app.hahn.tukplus.core.domain.LinePrice
import app.hahn.tukplus.ui.common.TukImage
import app.hahn.tukplus.ui.common.baht
import app.hahn.tukplus.ui.common.fulfilmentLabel
import app.hahn.tukplus.ui.theme.PictureShapes
import app.hahn.tukplus.ui.theme.TukIcons

/**
 * The cart (docs/design.md, PLAN.md §6): lines with quantities, the order type, the order
 * note and the amounts. Checkout comes in phase 4.
 */
@Composable
fun CartScreen(onBack: () -> Unit, onOpenShop: (String) -> Unit, viewModel: CartViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val cart = state.cart
    var askClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(TukIcons.Back, contentDescription = stringResource(R.string.back)) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(stringResource(R.string.cart_title), style = MaterialTheme.typography.headlineSmall)
                if (cart != null) Text(cart.shopName, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            if (cart != null) {
                IconButton(onClick = { askClear = true }) { Icon(TukIcons.Trash, contentDescription = stringResource(R.string.cart_clear)) }
            }
        }

        if (cart == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(TukIcons.Cart, contentDescription = null, tint = colors.primary, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.cart_empty), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.cart_empty_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            return
        }

        val blocked = cart.lines.mapNotNull { line -> CartRules.blockedReason(line)?.let { line.lineId to it } }.toMap()
        val itemsTotal = cartItemTotal(cart)
        val minOrder = state.business?.commerceWorkflow?.data?.minOrder ?: 0
        val belowMin = state.fulfilment == "delivery" && minOrder > 0 && itemsTotal < minOrder

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (state.changes.isNotEmpty()) {
                item(key = "changes") { ChangesCard(state.changes, onDismiss = viewModel::dismissChanges) }
            }
            items(cart.lines, key = { it.lineId }) { line ->
                LineRow(line, blocked[line.lineId], onQuantity = { viewModel.setQuantity(line.lineId, it) })
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.surfaceContainerHigh)
            }
            item(key = "more") {
                OutlinedButton(
                    onClick = { onOpenShop(cart.businessId) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(16.dp).height(48.dp),
                ) {
                    Icon(TukIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cart_add_more))
                }
            }
            item(key = "type") {
                Section(stringResource(R.string.cart_order_type)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        state.fulfilmentOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = option == state.fulfilment,
                                onClick = { viewModel.setFulfilment(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, state.fulfilmentOptions.size),
                            ) { Text(fulfilmentLabel(option)) }
                        }
                    }
                }
            }
            item(key = "note") {
                var note by rememberSaveable(cart.businessId) { mutableStateOf(cart.notes) }
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        viewModel.setNotes(it)
                    },
                    label = { Text(stringResource(R.string.cart_order_note)) },
                    placeholder = { Text(stringResource(R.string.cart_order_note_hint)) },
                    shape = RoundedCornerShape(16.dp),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            item(key = "amounts") {
                Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmountRow(stringResource(R.string.cart_items_total), baht(itemsTotal))
                        if (state.fulfilment == "delivery") AmountRow(stringResource(R.string.cart_delivery_fee), stringResource(R.string.cart_delivery_at_checkout))
                        HorizontalDivider(color = colors.surfaceContainerHighest)
                        AmountRow(stringResource(R.string.cart_total), baht(itemsTotal), strong = true)
                        if (belowMin) {
                            Text(
                                stringResource(R.string.cart_min_order, baht(minOrder), baht(minOrder - itemsTotal)),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.error,
                            )
                        }
                    }
                }
            }
        }

        val context = LocalContext.current
        val later = stringResource(R.string.cart_checkout_later)
        val closed = state.openState?.isOpen == false
        Surface(color = colors.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
                if (closed) {
                    Text(stringResource(R.string.cart_shop_closed), style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(bottom = 8.dp))
                }
                Button(
                    onClick = { Toast.makeText(context, later, Toast.LENGTH_SHORT).show() },
                    enabled = blocked.isEmpty() && !belowMin && !closed,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.cart_checkout, baht(itemsTotal)), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text(stringResource(R.string.cart_clear_title)) },
            text = { Text(stringResource(R.string.cart_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    askClear = false
                    viewModel.clear()
                }) { Text(stringResource(R.string.cart_clear)) }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text(stringResource(R.string.cart_new_no)) } },
        )
    }
}

@Composable
private fun LineRow(line: CartLine, blocked: Blocked?, onQuantity: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val item = LinePrice.menuItem(line)
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        TukImage(item?.pic, Modifier.size(72.dp), shape = PictureShapes.forKey(line.itemId), alpha = if (blocked != null) 0.5f else 1f)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(LinePrice.name(line), style = MaterialTheme.typography.titleMedium)
            val options = LinePrice.optionNames(line)
            if (options.isNotEmpty()) Text(options.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (line.note.isNotBlank()) Text("“${line.note}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)
            when (blocked) {
                Blocked.SoldOut -> Text(stringResource(R.string.cart_blocked_sold_out), style = MaterialTheme.typography.bodySmall, color = colors.error)
                is Blocked.Option -> Text(stringResource(R.string.cart_blocked_option, blocked.name), style = MaterialTheme.typography.bodySmall, color = colors.error)
                null -> Unit
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(baht(LinePrice.previewUnit(line) * line.quantity), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Stepper(line.quantity, onQuantity)
            }
        }
    }
}

/** − quantity +. At 1, the minus button is a trash button that removes the line. */
@Composable
private fun Stepper(quantity: Int, onQuantity: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHighest) {
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onQuantity(quantity - 1) }, modifier = Modifier.size(44.dp, 40.dp)) {
                if (quantity <= 1) {
                    Icon(TukIcons.Trash, contentDescription = stringResource(R.string.cart_remove), modifier = Modifier.size(18.dp))
                } else {
                    Icon(TukIcons.Minus, contentDescription = stringResource(R.string.item_less), modifier = Modifier.size(18.dp))
                }
            }
            Text(quantity.toString(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
            IconButton(
                onClick = { onQuantity(quantity + 1) },
                enabled = quantity < CartRules.MAX_QUANTITY,
                modifier = Modifier.size(44.dp, 40.dp),
            ) { Icon(TukIcons.Plus, contentDescription = stringResource(R.string.item_more), modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun ChangesCard(changes: List<LineChange>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.cart_changes_title), style = MaterialTheme.typography.titleMedium)
            for (change in changes) {
                Text(
                    when (change) {
                        is LineChange.Removed -> stringResource(R.string.cart_change_removed, change.name)
                        is LineChange.SoldOut -> stringResource(R.string.cart_change_sold_out, change.name)
                        is LineChange.OptionUnavailable -> stringResource(R.string.cart_change_option, change.name, change.option)
                        is LineChange.PriceChanged -> stringResource(R.string.cart_change_price, change.name, baht(change.oldPrice), baht(change.newPrice))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cart_changes_ok)) }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun AmountRow(label: String, value: String, strong: Boolean = false) {
    val style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(value, style = style)
    }
}
