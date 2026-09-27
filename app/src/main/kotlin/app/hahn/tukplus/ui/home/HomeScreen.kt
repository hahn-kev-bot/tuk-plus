package app.hahn.tukplus.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hahn.tukplus.R
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.domain.TileAction
import app.hahn.tukplus.core.domain.TileActions
import app.hahn.tukplus.core.model.PageRow
import app.hahn.tukplus.core.model.PageTile
import app.hahn.tukplus.ui.common.CacheAgeChip
import app.hahn.tukplus.ui.common.ShopCard
import app.hahn.tukplus.ui.common.SkeletonList
import app.hahn.tukplus.ui.common.TukImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTileAction: (TileAction) -> Unit,
    onOpenShop: (String) -> Unit,
    onOpenEat: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PullToRefreshBox(
        isRefreshing = state.status.status == Cached.Status.Refreshing && !state.isEmpty,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    CacheAgeChip(state.status, onRefresh = viewModel::refresh)
                }
            }
            item {
                OutlinedCard(
                    onClick = onOpenSearch,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.search_hint), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onOpenEat) { Text(stringResource(R.string.home_all_shops)) }
                    TextButton(onClick = onOpenDebug) { Text(stringResource(R.string.home_open_debug)) }
                }
            }
            if (state.isEmpty && state.status.data == null) {
                item { SkeletonList() }
            }
            if (state.recent.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.home_recent)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.recent, key = { it.business.id }) { item ->
                            SmallShopTile(item.business.name, item.business.data?.productPicUrl ?: item.business.profilePicUrl) { onOpenShop(item.business.id) }
                        }
                    }
                }
            }
            items(state.rows, key = { it.page }) { row -> TileRow(row, onTileAction) }
            if (state.newShops.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.home_new_shops)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.newShops, key = { it.id }) { shop ->
                            SmallShopTile(shop.name.orEmpty(), shop.productPicUrl ?: shop.profilePicUrl) { onOpenShop(shop.id) }
                        }
                    }
                }
            }
            if (state.topEats.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.home_top_eats)) }
                items(state.topEats, key = { "top-" + it.business.id }) { item ->
                    ShopCard(item, onClick = { onOpenShop(item.business.id) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun TileRow(row: PageRow, onTileAction: (TileAction) -> Unit) {
    Column {
        row.title?.titleFor("en")?.let { SectionTitle(it) }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(row.tiles, key = { it.id }) { tile -> Tile(tile) { onTileAction(TileActions.parse(tile.tag)) } }
        }
    }
}

@Composable
private fun Tile(tile: PageTile, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        TukImage(tile.picFor("en"), Modifier.width(150.dp).height(100.dp))
        if (tile.showName) {
            Text(tile.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SmallShopTile(name: String, pic: String?, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        TukImage(pic, Modifier.size(96.dp))
        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
