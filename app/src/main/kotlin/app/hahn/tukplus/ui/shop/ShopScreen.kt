package app.hahn.tukplus.ui.shop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hahn.tukplus.R
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.domain.MenuRules
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.core.model.parseIntLikeJavaScript
import app.hahn.tukplus.ui.common.CacheAgeChip
import app.hahn.tukplus.ui.common.PriceText
import app.hahn.tukplus.ui.common.SkeletonList
import app.hahn.tukplus.ui.common.TukImage
import app.hahn.tukplus.ui.common.baht
import app.hahn.tukplus.ui.common.openStateText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShopScreen(onBack: () -> Unit, viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<MenuEntry?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(state.business?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            CacheAgeChip(state.status, onRefresh = viewModel::refresh)
        }
        if (state.notFound) {
            Text(stringResource(R.string.shop_not_found), Modifier.padding(16.dp))
            return@Column
        }
        // Index of the first list item of each section, for the category chips.
        val headerItems = 3
        val sectionStarts = remember(state.sections) {
            var index = headerItems
            state.sections.map { section -> index.also { index += 1 + section.entries.size } }
        }
        if (state.sections.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(state.sections) { i, section ->
                    FilterChip(selected = false, onClick = { scope.launch { listState.animateScrollToItem(sectionStarts[i]) } }, label = { Text(section.title) })
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = state.status.status == Cached.Status.Refreshing && state.menu != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                item { ShopHeader(state) }
                item {
                    state.preface?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.menuSearch,
                        onValueChange = viewModel::setMenuSearch,
                        placeholder = { Text(stringResource(R.string.shop_search_menu)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (state.menu == null) {
                    item { SkeletonList() }
                } else if (state.sections.isEmpty()) {
                    item { Text(stringResource(if (state.menuSearch.isBlank()) R.string.shop_menu_empty else R.string.shop_menu_no_match), Modifier.padding(16.dp)) }
                }
                state.sections.forEach { section ->
                    stickyHeader(key = "section-" + section.title) {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(section.entries, key = { section.title + "/" + it.item.id }) { entry ->
                        MenuRow(entry, onClick = { selected = entry })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    val entry = selected
    val menu = state.menu
    if (entry != null && menu != null) {
        ModalBottomSheet(onDismissRequest = { selected = null }) { ItemSheet(entry, menu) }
    }
}

@Composable
private fun ShopHeader(state: ShopUiState) {
    val business = state.business
    Column(Modifier.padding(horizontal = 16.dp)) {
        if (business == null) {
            SkeletonList(count = 1, itemHeight = 160.dp)
            return@Column
        }
        TukImage(business.bannerPicUrl ?: business.data?.productPicUrl, Modifier.fillMaxWidth().height(160.dp))
        Text(business.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        state.openState?.let { open ->
            Text(
                openStateText(open) + "  ·  " + state.todayText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (open.isOpen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        val fulfilment = business.commerceWorkflow?.data?.fulfilmentOptions.orEmpty()
        if (fulfilment.isNotEmpty()) {
            val labels = fulfilment.map { fulfilmentLabel(it) }
            Text(labels.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        }
        business.data?.blurb?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun fulfilmentLabel(kind: String): String = when (kind) {
    "delivery" -> stringResource(R.string.fulfil_delivery)
    "take-away" -> stringResource(R.string.fulfil_takeaway)
    "dine-in" -> stringResource(R.string.fulfil_dinein)
    else -> kind
}

@Composable
private fun MenuRow(entry: MenuEntry, onClick: () -> Unit) {
    val item = entry.item
    val soldOut = item.outOfStock == true
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp).alpha(if (soldOut) 0.5f else 1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.titleSmall)
            item.displayDescription?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PriceText(MenuRules.basePrice(item), MenuRules.discountedPrice(item))
                if (soldOut) Text(stringResource(R.string.sold_out), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (!item.pic.isNullOrBlank()) TukImage(item.pic, Modifier.size(72.dp))
    }
}

/** Item details with its option groups. Ordering comes in phase 2, so this is read-only. */
@Composable
private fun ItemSheet(entry: MenuEntry, menu: Menu) {
    val item = entry.item
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (!item.pic.isNullOrBlank()) TukImage(item.pic, Modifier.fillMaxWidth().height(220.dp))
        Text(item.displayName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        item.displayDescription?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PriceText(MenuRules.basePrice(item), MenuRules.discountedPrice(item)) }
        for (group in menu.groupsFor(item)) {
            val g = group.group
            Text(g.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            val rule = when {
                g.multipleConstraint == "exactly" && g.multipleN != null -> stringResource(R.string.option_exactly, g.multipleN!!)
                g.multipleConstraint == "up_to" && g.multipleN != null -> stringResource(R.string.option_up_to, g.multipleN!!)
                g.select == "multiple" -> stringResource(R.string.option_any)
                else -> stringResource(R.string.option_one)
            }
            Text(
                if (g.required == true) stringResource(R.string.option_required, rule) else rule,
                style = MaterialTheme.typography.labelSmall,
                color = if (g.required == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (option in g.items) {
                val price = option.price?.let(::parseIntLikeJavaScript) ?: 0
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).alpha(if (option.outOfStock == true) 0.5f else 1f)) {
                    Text(option.displayName, Modifier.weight(1f))
                    if (price != 0) Text("+" + baht(price))
                }
            }
        }
        Text(stringResource(R.string.ordering_later), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
    }
}
