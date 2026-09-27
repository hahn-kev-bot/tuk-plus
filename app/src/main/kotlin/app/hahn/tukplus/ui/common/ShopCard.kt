package app.hahn.tukplus.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.hahn.tukplus.core.domain.Distance
import app.hahn.tukplus.core.domain.ShopListItem

/** One shop in a list: picture, name, cuisine, badges, distance and open state. */
@Composable
fun ShopCard(item: ShopListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val business = item.business
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
            .alpha(if (item.openState.isOpen) 1f else 0.6f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TukImage(business.data?.productPicUrl ?: business.profilePicUrl, Modifier.size(72.dp))
        Column(Modifier.weight(1f)) {
            Text(business.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val cuisine = item.categories.cuisines.take(3).joinToString(", ")
            if (cuisine.isNotEmpty()) {
                Text(cuisine, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.categories.badges.isNotEmpty()) {
                Text(
                    item.categories.badges.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            val distance = Distance.pretty(item.distanceMetres)
            Text(
                listOfNotNull(openStateText(item.openState), distance.ifEmpty { null }).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.openState.isOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
    }
}
