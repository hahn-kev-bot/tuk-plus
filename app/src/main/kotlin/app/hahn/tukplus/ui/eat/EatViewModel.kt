package app.hahn.tukplus.ui.eat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hahn.tukplus.core.data.BrowseRepository
import app.hahn.tukplus.core.data.Cached
import app.hahn.tukplus.core.data.Prefetcher
import app.hahn.tukplus.core.domain.EatPresetKind
import app.hahn.tukplus.core.domain.EatQuery
import app.hahn.tukplus.core.domain.EatResult
import app.hahn.tukplus.core.domain.EatSort
import app.hahn.tukplus.core.domain.ShopList
import app.hahn.tukplus.core.domain.TileAction
import app.hahn.tukplus.core.domain.TileActions
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.PageBlobs
import app.hahn.tukplus.core.network.TukApi
import app.hahn.tukplus.platform.LocationProvider
import app.hahn.tukplus.ui.common.chiangMaiTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import javax.inject.Inject

/** A chip at the top of the list, from the Eat page blobs (api-reference §4). */
data class EatChip(val label: String, val action: TileAction)

data class EatUiState(
    val chips: List<EatChip> = emptyList(),
    val query: EatQuery = EatQuery(),
    val result: EatResult = EatResult(emptyList(), emptyList()),
    val hasLocation: Boolean = false,
    val status: Cached<Unit> = Cached.empty(),
)

@HiltViewModel
class EatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val browse: BrowseRepository,
    private val prefetcher: Prefetcher,
    val location: LocationProvider,
    clock: Clock,
    private val log: TukLog,
) : ViewModel() {
    private val query = MutableStateFlow(
        EatQuery(
            text = savedState.get<String>("q").orEmpty(),
            preset = savedState.get<String>("preset")?.let { name -> EatPresetKind.entries.firstOrNull { it.name == name } },
        ).let { if (it.preset == EatPresetKind.FOR_YOU) it.copy(sort = EatSort.FOR_YOU) else it },
    )

    init {
        location.update()
    }

    private val items = combine(browse.eateries.state, browse.forYou.state, location.location, chiangMaiTicker(clock)) { eateries, forYou, here, now ->
        val scores = forYou.data.orEmpty().associate { it.key to (it.score ?: 0.0) }
        Triple(ShopList.build(eateries.data.orEmpty(), now, here, scores), here != null, Cached.combineStatus(listOf(eateries, forYou)))
    }

    val state: StateFlow<EatUiState> = combine(items, query, browse.eatChips.state) { (list, hasLocation, status), q, chipBlobs ->
        val chips = PageBlobs.toRow(TukApi.EAT_PAGE, chipBlobs.data.orEmpty()).tiles
            .filter { !it.hidden }
            .map { EatChip(it.name, TileActions.parse(it.tag)) }
            .filter { it.action is TileAction.EatSearch || it.action is TileAction.EatPreset }
        EatUiState(chips, q, ShopList.apply(list, q), hasLocation, status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EatUiState(query = query.value))

    fun setText(text: String) = query.update { it.copy(text = text, preset = null) }

    fun applyChip(chip: EatChip) {
        log.i("ui", "eat_chip", "label" to chip.label)
        when (val action = chip.action) {
            is TileAction.EatSearch -> query.update { if (it.text == action.text) it.copy(text = "") else it.copy(text = action.text, preset = null) }
            is TileAction.EatPreset -> query.update {
                if (it.preset == action.preset) it.copy(preset = null)
                else it.copy(preset = action.preset, sort = if (action.preset == EatPresetKind.FOR_YOU) EatSort.FOR_YOU else it.sort)
            }
            else -> Unit
        }
    }

    fun toggleOpenNow() = query.update { it.copy(openNowOnly = !it.openNowOnly) }

    fun toggleFulfilment(kind: String) = query.update {
        it.copy(fulfilment = if (kind in it.fulfilment) it.fulfilment - kind else it.fulfilment + kind)
    }

    fun setSort(sort: EatSort) = query.update { it.copy(sort = sort) }

    fun onLocationPermission(granted: Boolean) {
        log.i("location", "permission", "granted" to granted)
        if (granted) location.update()
    }

    /** A card stayed on screen: load its menu before the user taps it (PLAN.md §5.4). */
    fun onShopVisible(businessId: String) = prefetcher.onShopVisible(businessId)

    fun refresh() {
        log.i("ui", "refresh", "screen" to "eat")
        browse.eateries.refresh()
        browse.forYou.refresh()
        browse.eatChips.refresh()
    }
}
