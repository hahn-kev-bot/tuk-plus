package app.hahn.tukplus.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hahn.tukplus.R
import app.hahn.tukplus.core.domain.OpenState
import coil3.compose.AsyncImage
import java.time.format.DateTimeFormatter

/** A picture from the API, clipped to [shape]. Shows a plain box while it loads or when there is no picture. */
@Composable
fun TukImage(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    contentScale: ContentScale = ContentScale.Crop,
    alpha: Float = 1f,
) {
    val shaped = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (url.isNullOrBlank()) {
        Box(shaped)
    } else {
        AsyncImage(model = url, contentDescription = null, modifier = shaped, contentScale = contentScale, alpha = alpha)
    }
}

/** A grey box that stands for content that is still loading (PLAN.md §5.6: skeletons, not spinners). */
@Composable
fun SkeletonBlock(height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun SkeletonList(count: Int = 6, itemHeight: Dp = 72.dp) {
    Column(Modifier.padding(16.dp)) {
        repeat(count) {
            SkeletonBlock(itemHeight)
            Box(Modifier.height(12.dp))
        }
    }
}

/** "฿380". Thai shops only in release 1. */
fun baht(amount: Int): String = "฿" + "%,d".format(amount)

/** A price, with the old price struck through when there is a discount. Use it in a Row. */
@Composable
fun PriceText(base: Int?, discounted: Int?, color: Color = MaterialTheme.colorScheme.onSurface) {
    if (base == null) return
    val strong = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp)
    if (discounted != null && discounted < base) {
        Text(baht(discounted), style = strong, color = MaterialTheme.colorScheme.tertiary)
        Text(
            baht(base),
            style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(baht(base), style = strong, color = color)
    }
}

/** A small filled label, for example "Top rated", "New", "Sold out", "Required". */
@Composable
fun Badge(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(container).padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Short text for the open state of a shop. */
@Composable
fun openStateText(state: OpenState): String = when (state) {
    is OpenState.Open -> state.closesAt?.let { stringResource(R.string.open_until, it.format(timeFormat)) } ?: stringResource(R.string.open_now)
    is OpenState.ClosedNow -> state.opensAt?.let { stringResource(R.string.opens_at, it.format(timeFormat)) } ?: stringResource(R.string.closed_now)
    OpenState.ClosedToday -> stringResource(R.string.closed_today)
    is OpenState.OnHoliday -> stringResource(R.string.on_holiday)
    OpenState.Paused -> stringResource(R.string.paused)
    OpenState.NotOrderable -> stringResource(R.string.not_orderable)
}
