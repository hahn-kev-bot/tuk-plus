package app.hahn.tukplus.ui.home

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
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
import app.hahn.tukplus.core.domain.EatPresetKind
import app.hahn.tukplus.core.domain.TileAction
import app.hahn.tukplus.core.domain.TileActions
import app.hahn.tukplus.core.model.PageRow
import app.hahn.tukplus.ui.common.CacheAgeChip
import app.hahn.tukplus.ui.common.ShopCard
import app.hahn.tukplus.ui.common.SkeletonList
import app.hahn.tukplus.ui.common.TukImage
import app.hahn.tukplus.ui.theme.PictureShapes
import app.hahn.tukplus.ui.theme.TukIcons

/** Quick filters under the search bar. They open the shops list. */
private data class QuickFilter(val label: Int, val action: TileAction)

private val QUICK_FILTERS = listOf(
    QuickFilter(R.string.filter_open_now, TileAction.EatPreset(EatPresetKind.OPEN_NOW)),
    QuickFilter(R.string.quick_buy1get1, TileAction.EatSearch("buy1get1")),
    QuickFilter(R.string.quick_15_off, TileAction.EatSearch("15%")),
    QuickFilter(R.string.quick_free_delivery, TileAction.EatPreset(EatPresetKind.FREE_DELIVERY)),
    QuickFilter(R.string.quick_vegan, TileAction.EatSearch("vegan")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onTileAction: (TileAction) -> Unit,
    onOpenShop: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAccount: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    // A row whose tiles only filter the shop list (cuisines) shows as text tiles.
    val (textRows, pictureRows) = state.rows.partition { row ->
        row.tiles.isNotEmpty() && row.tiles.all { TileActions.parse(it.tag).let { a -> a is TileAction.EatSearch || a is TileAction.EatPreset } }
    }

    PullToRefreshBox(
        isRefreshing = state.status.status == Cached.Status.Refreshing && !state.isEmpty,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainer) {
                        Row(Modifier.height(44.dp).padding(start = 10.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(TukIcons.Pin, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.home_area), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onOpenAccount, modifier = Modifier.size(44.dp)) {
                        Icon(TukIcons.Person, contentDescription = stringResource(R.string.nav_account), modifier = Modifier.size(22.dp))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.home_headline),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }
            item {
                Surface(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(28.dp),
                    color = colors.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Row(Modifier.height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(TukIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                        Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QUICK_FILTERS) { filter ->
                        FilterChip(selected = false, onClick = { onTileAction(filter.action) }, label = { Text(stringResource(filter.label)) })
                    }
                }
            }
            if (state.isEmpty && state.status.data == null) {
                item { SkeletonList() }
            }
            val first = pictureRows.firstOrNull()
            item(key = "carousel-" + (first?.page ?: "none")) {
                SectionTitle(first?.title?.titleFor("en") ?: stringResource(R.string.home_whats_hot)) {
                    CacheAgeChip(state.status, onRefresh = viewModel::refresh)
                }
                if (first != null) Carousel(first, onTileAction)
            }
            items(textRows, key = { "text-" + it.page }) { row -> TextTileRow(row, onTileAction) }
            if (state.recent.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.home_recent)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.recent, key = { it.business.id }) { item ->
                            SmallShopTile(item.business.id, item.business.name, item.business.data?.productPicUrl ?: item.business.profilePicUrl) {
                                onOpenShop(item.business.id)
                            }
                        }
                    }
                }
            }
            items(pictureRows.drop(1), key = { "pics-" + it.page }) { row -> PictureRow(row, onTileAction) }
            if (state.newShops.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.home_new_shops)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.newShops, key = { it.id }) { shop ->
                            SmallShopTile(shop.id, shop.name.orEmpty(), shop.productPicUrl ?: shop.profilePicUrl) { onOpenShop(shop.id) }
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
private fun SectionTitle(text: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** The first picture row: a Material 3 carousel (one large item, smaller ones peek in). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Carousel(row: PageRow, onTileAction: (TileAction) -> Unit) {
    val tiles = row.tiles
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { tiles.size },
        preferredItemWidth = 264.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth().height(200.dp),
    ) { index ->
        val tile = tiles[index]
        Box(
            Modifier.fillMaxSize().maskClip(RoundedCornerShape(28.dp)).clickable { onTileAction(TileActions.parse(tile.tag)) },
        ) {
            TukImage(tile.picFor("en"), Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
        }
    }
}

/** Other picture rows: pictures with mixed shapes. */
@Composable
private fun PictureRow(row: PageRow, onTileAction: (TileAction) -> Unit) {
    Column {
        SectionTitle(row.title?.titleFor("en").orEmpty())
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(row.tiles, key = { _, tile -> tile.id }) { index, tile ->
                Column(Modifier.width(150.dp).clickable { onTileAction(TileActions.parse(tile.tag)) }) {
                    TukImage(tile.picFor("en"), Modifier.width(150.dp).height(110.dp), shape = PictureShapes.forIndex(index))
                    if (tile.showName) {
                        Text(tile.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

/** A row of filters (for example cuisines) as text tiles with mixed shapes and colors. */
@Composable
private fun TextTileRow(row: PageRow, onTileAction: (TileAction) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fills = listOf(
        colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer,
        colors.tertiaryContainer to colors.onTertiaryContainer,
        colors.surfaceContainerHighest to colors.onSurface,
    )
    Column {
        SectionTitle(row.title?.titleFor("en").orEmpty())
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(row.tiles, key = { _, tile -> tile.id }) { index, tile ->
                val (fill, text) = fills[index % fills.size]
                Surface(
                    onClick = { onTileAction(TileActions.parse(tile.tag)) },
                    shape = PictureShapes.tiles[index % PictureShapes.tiles.size],
                    color = fill,
                    contentColor = text,
                ) {
                    Box(Modifier.size(width = 96.dp, height = 72.dp), contentAlignment = Alignment.Center) {
                        Text(tile.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallShopTile(key: String, name: String, pic: String?, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TukImage(pic, Modifier.width(120.dp).height(96.dp), shape = PictureShapes.forKey(key))
        Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
