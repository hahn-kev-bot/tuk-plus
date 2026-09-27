package app.hahn.tukplus.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hahn.tukplus.R
import app.hahn.tukplus.core.data.CartQuote
import app.hahn.tukplus.core.domain.LinePrice
import app.hahn.tukplus.ui.common.baht
import app.hahn.tukplus.ui.theme.TukIcons

/**
 * The amount for the bar: the total before the delivery fee, from `core:pricing`. Until the
 * shop settings are loaded, a preview from the saved lines.
 */
fun barTotal(cart: CartQuote): Int =
    cart.quote?.total ?: cart.cart.lines.sumOf { LinePrice.previewUnit(it) * it.quantity }

/**
 * The "View cart" bar: item count, shop name (when [showShop]) and item total.
 * The shop menu shows it at the bottom; the main tabs show it above the navigation bar.
 */
@Composable
fun CartBar(quote: CartQuote, showShop: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val cart = quote.cart
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = colors.primary,
        contentColor = colors.onPrimary,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Surface(shape = CircleShape, color = colors.onPrimary, contentColor = colors.primary, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(cart.itemCount.toString(), style = MaterialTheme.typography.titleSmall) }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cart_view), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (showShop) cart.shopName else pluralStringResource(R.plurals.cart_items, cart.itemCount, cart.itemCount),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(baht(barTotal(quote)), style = MaterialTheme.typography.titleMedium)
            Icon(TukIcons.Cart, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}
