package app.hahn.tukplus.ui.cart

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hahn.tukplus.core.data.BrowseRepository
import app.hahn.tukplus.core.data.CartQuotes
import app.hahn.tukplus.core.data.CartStore
import app.hahn.tukplus.core.data.ShopRepository
import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartRules
import app.hahn.tukplus.core.domain.LineChange
import app.hahn.tukplus.core.domain.OpenHours
import app.hahn.tukplus.core.domain.OpenState
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.pricing.Quote
import app.hahn.tukplus.core.model.MenuParser
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.ui.common.chiangMaiTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class CartUiState(
    val cart: Cart? = null,
    /** The amounts from `core:pricing`. Null while the shop settings are not loaded. */
    val quote: Quote? = null,
    val business: Business? = null,
    val openState: OpenState? = null,
    /** The order types that the shop offers, in cart screen order. */
    val fulfilmentOptions: List<String> = emptyList(),
    /** The order type in use (the user's choice or the first that the shop offers). */
    val fulfilment: String = CartRules.FULFILMENT_ORDER.first(),
    val changes: List<LineChange> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CartViewModel @Inject constructor(
    private val store: CartStore,
    quotes: CartQuotes,
    browse: BrowseRepository,
    private val shops: ShopRepository,
    private val clock: Clock,
    private val log: TukLog,
) : ViewModel() {
    private val businessId = store.cart.map { it?.businessId }.distinctUntilChanged()

    private val business = businessId.flatMapLatest { id ->
        if (id == null) flowOf(null) else browse.eateries.state.flatMapLatest { list ->
            list.data?.firstOrNull { it.id == id }?.let { flowOf(it) } ?: shops.business(id).also { it.start() }.state.map { it.data }
        }
    }

    init {
        // One log line when the cart screen has its amounts (to compare with tukapp.co).
        viewModelScope.launch {
            val priced = quotes.state.first { it?.quote != null }!!
            val q = priced.quote!!
            log.i(
                "cart", "quote",
                "business_id" to priced.cart.businessId,
                "lines" to priced.cart.lines.size,
                "fulfilment" to priced.fulfilment,
                "subtotal" to q.subtotal,
                "vat" to q.vat,
                "discount" to q.fulfilmentDiscount,
                "total" to q.total,
                "free_delivery_over" to q.freeDeliveryOver,
                "blocked" to q.blocked.joinToString(","),
                "web_only" to q.webOnly?.name,
            )
        }
        // Check the cart against the newest menu (PLAN.md §5.7). The shop screen does the same.
        viewModelScope.launch {
            businessId.flatMapLatest { id ->
                if (id == null) flowOf(null) else shops.menu(id).also { it.start() }.state.map { cached ->
                    cached.data?.firstOrNull { it.workflowTypeName == Workflow.TYPE_COMMERCE }?.let(MenuParser::parse)
                }
            }.collect { menu -> menu?.let(store::reconcile) }
        }
    }

    val state: StateFlow<CartUiState> = combine(quotes.state, business, store.changes, chiangMaiTicker(clock)) { priced, shop, changes, now ->
        CartUiState(
            cart = priced?.cart,
            quote = priced?.quote,
            business = shop,
            openState = shop?.let { OpenHours.state(it, now) },
            fulfilmentOptions = priced?.fulfilmentOptions ?: listOf(CartRules.FULFILMENT_ORDER.first()),
            fulfilment = priced?.fulfilment ?: CartRules.FULFILMENT_ORDER.first(),
            changes = changes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState(cart = store.cart.value))

    fun setQuantity(lineId: String, quantity: Int) {
        log.i("cart", "quantity", "quantity" to quantity)
        store.update { cart -> cart?.let { CartRules.setQuantity(it, lineId, quantity, clock.millis()) } }
    }

    fun remove(lineId: String) {
        log.i("cart", "remove")
        store.update { cart -> cart?.let { CartRules.remove(it, lineId, clock.millis()) } }
    }

    fun clear() {
        log.i("cart", "clear")
        store.clear()
    }

    fun setNotes(notes: String) {
        store.update { cart -> cart?.let { CartRules.setNotes(it, notes, clock.millis()) } }
    }

    fun setFulfilment(fulfilment: String) {
        log.i("cart", "fulfilment", "value" to fulfilment)
        store.update { cart -> cart?.let { CartRules.setFulfilment(it, fulfilment, clock.millis()) } }
    }

    fun dismissChanges() = store.dismissChanges()
}
