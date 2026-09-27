package app.hahn.tukplus.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hahn.tukplus.R
import app.hahn.tukplus.core.data.Cached
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

/** The current time, updated every [periodMs]. Screens use it for ages and for "open now". */
@Composable
fun rememberNow(periodMs: Long = 30_000): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = Instant.now()
        }
    }
    return now
}

/** "3 min", "2 h", "1 day". */
@Composable
fun ageText(age: Duration): String = when {
    age.toMinutes() < 1 -> stringResource(R.string.age_now)
    age.toMinutes() < 60 -> stringResource(R.string.age_minutes, age.toMinutes())
    age.toHours() < 48 -> stringResource(R.string.age_hours, age.toHours())
    else -> pluralStringResource(R.plurals.age_days, age.toDays().toInt(), age.toDays())
}

/**
 * Shows how old the data on the screen is (PLAN.md §5.1, rule 3). A tap starts a refresh.
 * "Updated 3 min ago", "Updating…", "Offline – data from 2 h ago".
 */
@Composable
fun CacheAgeChip(cached: Cached<*>, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val age = cached.age(now)
    val status = cached.status
    val text = when {
        status is Cached.Status.Refreshing && age == null -> stringResource(R.string.cache_loading)
        status is Cached.Status.Refreshing -> stringResource(R.string.cache_updating, ageText(age!!))
        status is Cached.Status.Error && age == null -> stringResource(if (status.offline) R.string.cache_offline_empty else R.string.cache_error_empty)
        status is Cached.Status.Error -> stringResource(if (status.offline) R.string.cache_offline else R.string.cache_error, ageText(age!!))
        age == null -> stringResource(R.string.cache_loading)
        else -> stringResource(R.string.cache_updated, ageText(age))
    }
    val isProblem = status is Cached.Status.Error
    AssistChip(
        onClick = onRefresh,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier.padding(vertical = 2.dp),
        colors = if (isProblem) {
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                labelColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}
