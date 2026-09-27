package app.hahn.tukplus.ui.eat

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hahn.tukplus.R
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.domain.EatPresetKind
import app.hahn.tukplus.core.domain.EatSort
import app.hahn.tukplus.core.domain.ShopListItem
import app.hahn.tukplus.core.domain.TileAction
import app.hahn.tukplus.ui.common.CacheAgeChip
import app.hahn.tukplus.ui.common.ShopCard
import app.hahn.tukplus.ui.common.SkeletonList
import app.hahn.tukplus.ui.theme.TukIcons
import kotlinx.coroutines.delay

private val FULFILMENT = listOf("delivery" to R.string.fulfil_delivery, "take-away" to R.string.fulfil_takeaway, "dine-in" to R.string.fulfil_dinein)
private val SORTS = listOf(EatSort.FOR_YOU to R.string.sort_for_you, EatSort.DISTANCE to R.string.sort_distance, EatSort.NAME to R.string.sort_name, EatSort.NEWEST to R.string.sort_new)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatScreen(onOpenShop: (String) -> Unit, onOpenSearch: () -> Unit, viewModel: EatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onLocationPermission(it) }
    val query = state.query

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.eat_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            CacheAgeChip(state.status, onRefresh = viewModel::refresh)
            IconButton(onClick = onOpenSearch) { Icon(TukIcons.Search, contentDescription = stringResource(R.string.search_hint)) }
        }
        OutlinedTextField(
            value = query.text,
            onValueChange = viewModel::setText,
            placeholder = { Text(stringResource(R.string.eat_filter_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = query.openNowOnly || query.preset == EatPresetKind.OPEN_NOW, onClick = viewModel::toggleOpenNow, label = { Text(stringResource(R.string.filter_open_now)) })
            }
            items(FULFILMENT) { (kind, label) ->
                FilterChip(selected = kind in query.fulfilment, onClick = { viewModel.toggleFulfilment(kind) }, label = { Text(stringResource(label)) })
            }
            items(state.chips) { chip ->
                val selected = when (val action = chip.action) {
                    is TileAction.EatSearch -> query.text.equals(action.text, ignoreCase = true)
                    is TileAction.EatPreset -> query.preset == action.preset
                    else -> false
                }
                FilterChip(selected = selected, onClick = { viewModel.applyChip(chip) }, label = { Text(chip.label) })
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(SORTS) { (sort, label) ->
                FilterChip(selected = query.sort == sort, onClick = { viewModel.setSort(sort) }, label = { Text(stringResource(label)) })
            }
            if (!state.hasLocation) {
                item {
                    TextButton(onClick = { permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                        Text(stringResource(R.string.use_location))
                    }
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.status.status == Cached.Status.Refreshing && state.status.data != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            val result = state.result
            if (state.status.data == null) {
                SkeletonList()
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { ListTitle(stringResource(R.string.eat_open_count, result.open.size)) }
                    items(result.open, key = { it.business.id }) { item -> VisibleShopCard(item, viewModel, onOpenShop) }
                    if (result.closed.isNotEmpty()) {
                        item { ListTitle(stringResource(R.string.eat_closed_count, result.closed.size)) }
                        items(result.closed, key = { "closed-" + it.business.id }) { item -> VisibleShopCard(item, viewModel, onOpenShop) }
                    }
                    if (result.open.isEmpty() && result.closed.isEmpty()) {
                        item { Text(stringResource(R.string.eat_no_match), Modifier.padding(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun VisibleShopCard(item: ShopListItem, viewModel: EatViewModel, onOpenShop: (String) -> Unit) {
    // If the card stays on screen for 1 s, load its menu (PLAN.md §5.4).
    LaunchedEffect(item.business.id) {
        delay(1_000)
        viewModel.onShopVisible(item.business.id)
    }
    ShopCard(item, onClick = { onOpenShop(item.business.id) })
}
