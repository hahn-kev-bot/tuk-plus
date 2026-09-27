package app.hahn.tukplus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hahn.tukplus.R
import app.hahn.tukplus.core.domain.Distance
import app.hahn.tukplus.core.domain.ShopListItem
import app.hahn.tukplus.ui.theme.PictureShapes

/** One shop in a list (design: "Picked for you" card). Closed shops are dimmed. */
@Composable
fun ShopCard(item: ShopListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val business = item.business
    val open = item.openState.isOpen
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TukImage(
                business.data?.productPicUrl ?: business.profilePicUrl,
                Modifier.size(88.dp),
                shape = PictureShapes.forKey(business.id),
                alpha = if (open) 1f else 0.55f,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        business.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
                        color = if (open) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.categories.badges.firstOrNull()?.let { Badge(it, colors.tertiaryContainer, colors.onTertiaryContainer) }
                }
                val details = listOfNotNull(
                    item.categories.cuisines.take(2).joinToString(" · ").ifEmpty { null },
                    Distance.pretty(item.distanceMetres).ifEmpty { null },
                ).joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val fulfilment = business.commerceWorkflow?.data?.fulfilmentOptions.orEmpty().map { fulfilmentLabel(it) }
                Text(
                    listOf(openStateText(item.openState)).plus(if (open && fulfilment.isNotEmpty()) listOf(fulfilment.joinToString(", ")) else emptyList())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (open) colors.primary else colors.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Delivery", "Take-away", "Dine-in". */
@Composable
fun fulfilmentLabel(kind: String): String = when (kind) {
    "delivery" -> stringResource(R.string.fulfil_delivery)
    "take-away" -> stringResource(R.string.fulfil_takeaway)
    "dine-in" -> stringResource(R.string.fulfil_dinein)
    else -> kind
}
