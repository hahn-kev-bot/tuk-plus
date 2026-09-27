package app.hahn.tukplus.ui.shop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hahn.tukplus.R
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.domain.Categories
import app.hahn.tukplus.core.domain.MenuRules
import app.hahn.tukplus.core.domain.MenuSection
import app.hahn.tukplus.core.domain.OpenState
import app.hahn.tukplus.core.model.Menu
import app.hahn.tukplus.core.model.MenuEntry
import app.hahn.tukplus.platform.MenuView
import app.hahn.tukplus.ui.cart.CartBar
import app.hahn.tukplus.ui.common.Badge
import app.hahn.tukplus.ui.common.PriceText
import app.hahn.tukplus.ui.common.SkeletonList
import app.hahn.tukplus.ui.common.TukImage
import app.hahn.tukplus.ui.common.ageText
import app.hahn.tukplus.ui.common.fulfilmentLabel
import app.hahn.tukplus.ui.common.openStateText
import app.hahn.tukplus.ui.common.rememberNow
import app.hahn.tukplus.ui.theme.PictureShapes
import app.hahn.tukplus.ui.theme.TukIcons
import kotlinx.coroutines.launch

/** Draws the element [by] higher and takes that much less space (the info sheet over the photo). */
private fun Modifier.pullUp(by: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

/** List items before the first menu section: photo, info sheet, search field. */
private const val HEADER_ITEMS = 3

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShopScreen(onBack: () -> Unit, onOpenCart: () -> Unit, viewModel: ShopViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val pendingAdd by viewModel.pendingAdd.collectAsStateWithLifecycle()
    val cartHere = cart?.takeIf { it.businessId == state.business?.id }
    var selected by remember { mutableStateOf<MenuEntry?>(null) }
    var searchOpen by remember { mutableStateOf(state.menuSearch.isNotBlank()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Index of each section's header in the list. The sticky tab row is item HEADER_ITEMS.
    val sectionStarts = remember(state.sections, state.view) {
        var index = HEADER_ITEMS + 1
        state.sections.map { section ->
            val start = index
            val rows = if (state.view == MenuView.GRID) (section.entries.size + 1) / 2 else section.entries.size
            index += 1 + rows
            start
        }
    }
    val currentSection by remember(sectionStarts) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex + 1
            sectionStarts.indexOfLast { it <= first }.coerceAtLeast(0)
        }
    }

    if (state.notFound) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            RoundIconButton(TukIcons.Back, stringResource(R.string.back), onBack)
            Text(stringResource(R.string.shop_not_found), Modifier.padding(top = 16.dp))
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = state.status.status == Cached.Status.Refreshing && state.menu != null,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = if (cartHere != null) 104.dp else 32.dp)) {
            item(key = "photo") { HeroPhoto(state, onBack, onSearch = { searchOpen = !searchOpen }) }
            item(key = "info") { InfoSheet(state, onRefresh = viewModel::refresh) }
            item(key = "search") {
                if (searchOpen) {
                    OutlinedTextField(
                        value = state.menuSearch,
                        onValueChange = viewModel::setMenuSearch,
                        placeholder = { Text(stringResource(R.string.shop_search_menu)) },
                        leadingIcon = { Icon(TukIcons.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    )
                }
            }
            stickyHeader(key = "tabs") {
                TabsAndView(
                    sections = state.sections,
                    current = currentSection,
                    view = state.view,
                    onSection = { i -> scope.launch { listState.animateScrollToItem(sectionStarts[i]) } },
                    onView = viewModel::setView,
                )
            }
            when {
                state.menu == null -> item { SkeletonList() }
                state.sections.isEmpty() -> item {
                    Text(
                        stringResource(if (state.menuSearch.isBlank()) R.string.shop_menu_empty else R.string.shop_menu_no_match),
                        Modifier.padding(16.dp),
                    )
                }
                else -> state.sections.forEach { section -> menuSection(section, state.menu!!, state.view) { selected = it } }
            }
        }
        cartHere?.let { CartBar(it, showShop = false, onClick = onOpenCart, modifier = Modifier.align(Alignment.BottomCenter)) }
    }

    pendingAdd?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::keepOldCart,
            title = { Text(stringResource(R.string.cart_new_title)) },
            text = { Text(stringResource(R.string.cart_new_text, pending.otherShop)) },
            confirmButton = { TextButton(onClick = viewModel::startNewCart) { Text(stringResource(R.string.cart_new_yes)) } },
            dismissButton = { TextButton(onClick = viewModel::keepOldCart) { Text(stringResource(R.string.cart_new_no)) } },
        )
    }

    val entry = selected
    val menu = state.menu
    if (entry != null && menu != null) {
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ItemSheet(entry, menu, onClose = { selected = null }, onAdd = { addition ->
                selected = null
                viewModel.addToCart(addition)
            })
        }
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.size(44.dp),
    ) { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun HeroPhoto(state: ShopUiState, onBack: () -> Unit, onSearch: () -> Unit) {
    val business = state.business
    Box(Modifier.fillMaxWidth().height(280.dp)) {
        TukImage(business?.bannerPicUrl ?: business?.data?.productPicUrl, Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(TukIcons.Back, stringResource(R.string.back), onBack)
            Spacer(Modifier.weight(1f))
            RoundIconButton(TukIcons.Search, stringResource(R.string.shop_search_menu), onSearch)
        }
    }
}

@Composable
private fun InfoSheet(state: ShopUiState, onRefresh: () -> Unit) {
    val business = state.business
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = colors.surface,
        modifier = Modifier.fillMaxWidth().pullUp(32.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (business == null) {
                SkeletonList(count = 1, itemHeight = 72.dp)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                TukImage(business.profilePicUrl, Modifier.size(56.dp), shape = PictureShapes.Leaf)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(business.name, style = MaterialTheme.typography.headlineMedium)
                    val cuisines = Categories.parse(business.data?.categories).cuisines.take(3).joinToString(" · ")
                    if (cuisines.isNotEmpty()) Text(cuisines, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            state.openState?.let { open ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (open.isOpen) colors.primaryContainer else colors.errorContainer,
                        contentColor = if (open.isOpen) colors.onPrimaryContainer else colors.onErrorContainer,
                    ) {
                        Row(Modifier.height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(TukIcons.Clock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(openStateText(open), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (open !is OpenState.OnHoliday) {
                        Text(state.todayText, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            val workflow = business.commerceWorkflow?.data
            val fulfilment = workflow?.fulfilmentOptions.orEmpty().map { fulfilmentLabel(it) }
            val cash = workflow?.paymentOptions.orEmpty().contains("cash")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (fulfilment.isNotEmpty()) {
                    Text(fulfilment.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Text(
                    stringResource(if (cash) R.string.shop_pay_with_cash else R.string.shop_pay_no_cash),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                val age = state.status.age(rememberNow())
                Surface(onClick = onRefresh, color = colors.surface, contentColor = colors.onSurfaceVariant) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(32.dp)) {
                        Icon(TukIcons.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            when {
                                state.status.status is Cached.Status.Refreshing -> stringResource(R.string.cache_loading)
                                state.status.status is Cached.Status.Error && age != null -> stringResource(R.string.shop_menu_offline, ageText(age))
                                age != null -> stringResource(R.string.shop_menu_updated, ageText(age))
                                else -> stringResource(R.string.cache_loading)
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsAndView(
    sections: List<MenuSection>,
    current: Int,
    view: MenuView,
    onSection: (Int) -> Unit,
    onView: (MenuView) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.background(colors.surface)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (sections.size > 1) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = current.coerceIn(0, sections.lastIndex),
                        edgePadding = 12.dp,
                        containerColor = colors.surface,
                        divider = {},
                    ) {
                        sections.forEachIndexed { i, section ->
                            Tab(
                                selected = i == current,
                                onClick = { onSection(i) },
                                text = { Text(section.title, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                                selectedContentColor = colors.primary,
                                unselectedContentColor = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Surface(shape = RoundedCornerShape(18.dp), color = colors.surfaceContainer, modifier = Modifier.padding(start = 4.dp, end = 12.dp)) {
                Row(Modifier.padding(2.dp)) {
                    ViewButton(TukIcons.List, stringResource(R.string.menu_view_list), view == MenuView.LIST) { onView(MenuView.LIST) }
                    ViewButton(TukIcons.Grid, stringResource(R.string.menu_view_grid), view == MenuView.GRID) { onView(MenuView.GRID) }
                }
            }
        }
        HorizontalDivider(color = colors.surfaceContainerHighest)
    }
}

@Composable
private fun ViewButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        modifier = Modifier.size(width = 44.dp, height = 36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) }
    }
}

private fun LazyListScope.menuSection(section: MenuSection, menu: Menu, view: MenuView, onSelect: (MenuEntry) -> Unit) {
    item(key = "title-" + section.title) {
        Text(
            section.title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
    if (view == MenuView.GRID) {
        val rows = section.entries.chunked(2)
        rows.forEachIndexed { r, pair ->
            item(key = "grid-" + section.title + "/" + r) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { entry -> GridCard(entry, Modifier.weight(1f)) { onSelect(entry) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        section.entries.forEach { entry ->
            item(key = "row-" + section.title + "/" + entry.item.id) {
                ListRow(entry, menu) { onSelect(entry) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest)
            }
        }
    }
}

/** The "+" badge on a dish picture. The cart comes in phase 2; now it opens the item sheet. */
@Composable
private fun AddBadge(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(size * 0.36f)),
        contentAlignment = Alignment.Center,
    ) { Icon(TukIcons.Plus, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size / 2)) }
}

@Composable
private fun optionHint(entry: MenuEntry, menu: Menu): String? {
    val groups = menu.groupsFor(entry.item)
    return when {
        groups.any { it.group.required == true } -> stringResource(R.string.menu_choose_options)
        groups.isNotEmpty() -> stringResource(R.string.menu_extras)
        else -> null
    }
}

@Composable
private fun ListRow(entry: MenuEntry, menu: Menu, onClick: () -> Unit) {
    val item = entry.item
    val soldOut = item.outOfStock == true
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = !soldOut, color = colors.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.displayName, style = MaterialTheme.typography.titleMedium, color = if (soldOut) colors.onSurfaceVariant else colors.onSurface)
                item.displayDescription?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PriceText(MenuRules.basePrice(item), MenuRules.discountedPrice(item), color = if (soldOut) colors.onSurfaceVariant else colors.onSurface)
                    if (soldOut) {
                        Badge(stringResource(R.string.sold_out), colors.surfaceContainerHighest, colors.onSurface)
                    } else {
                        optionHint(entry, menu)?.let { Text("· $it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    }
                }
            }
            if (!item.pic.isNullOrBlank()) {
                Box(Modifier.size(112.dp)) {
                    TukImage(item.pic, Modifier.fillMaxSize(), shape = PictureShapes.forKey(item.id), alpha = if (soldOut) 0.45f else 1f)
                    if (!soldOut) AddBadge(44.dp, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp))
                }
            } else if (!soldOut) {
                AddBadge(44.dp, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Composable
private fun GridCard(entry: MenuEntry, modifier: Modifier, onClick: () -> Unit) {
    val item = entry.item
    val soldOut = item.outOfStock == true
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = !soldOut, color = colors.surface, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                TukImage(item.pic, Modifier.fillMaxSize(), shape = PictureShapes.forKey(item.id), alpha = if (soldOut) 0.45f else 1f)
                if (soldOut) {
                    Badge(stringResource(R.string.sold_out), colors.surfaceContainerHighest, colors.onSurface, Modifier.padding(8.dp))
                } else {
                    AddBadge(40.dp, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }
            }
            Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PriceText(MenuRules.basePrice(item), MenuRules.discountedPrice(item), color = if (soldOut) colors.onSurfaceVariant else colors.onSurface)
            }
        }
    }
}

