package app.hahn.tukplus.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hahn.tukplus.R
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.ui.common.ShopCard
import app.hahn.tukplus.ui.common.TukImage
import app.hahn.tukplus.ui.theme.PictureShapes
import app.hahn.tukplus.ui.theme.TukIcons

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenShop: (businessId: String, menuSearch: String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(TukIcons.Back, contentDescription = stringResource(R.string.back)) }
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::setText,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(TukIcons.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.weight(1f).padding(end = 12.dp, top = 8.dp, bottom = 8.dp).focusRequester(focus),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.local, key = { "local-" + it.business.id }) { item ->
                ShopCard(item, onClick = { onOpenShop(item.business.id, null) })
            }
            val server = state.server
            if (server.status.status == Cached.Status.Refreshing) {
                item { Hint(stringResource(R.string.search_server_loading)) }
            }
            if (server.status.status is Cached.Status.Error) {
                item { Hint(stringResource(R.string.search_server_failed)) }
            }
            if (server.shops.isNotEmpty()) {
                item { Title(stringResource(R.string.search_more_shops)) }
                items(server.shops, key = { "server-" + it.value }) { entry ->
                    ResultRow(entry.pic, entry.key, null) { onOpenShop(entry.value, null) }
                }
            }
            if (server.menuHits.isNotEmpty()) {
                item { Title(stringResource(R.string.search_menu_items, state.text.trim())) }
                items(server.menuHits, key = { "menu-" + it.businessId }) { hit ->
                    val count = hit.count ?: 0
                    ResultRow(hit.businessPic, hit.businessName.orEmpty(), pluralStringResource(R.plurals.search_item_count, count, count)) {
                        onOpenShop(hit.businessId, state.text.trim())
                    }
                }
            }
            if (state.text.isNotBlank() && state.local.isEmpty() && server.shops.isEmpty() && server.menuHits.isEmpty() &&
                server.status.status !is Cached.Status.Refreshing
            ) {
                item { Hint(stringResource(R.string.search_nothing)) }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
}

@Composable
private fun ResultRow(pic: String?, name: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TukImage(pic, Modifier.size(56.dp), shape = PictureShapes.forKey(name))
        Column {
            Text(name, style = MaterialTheme.typography.titleSmall)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
