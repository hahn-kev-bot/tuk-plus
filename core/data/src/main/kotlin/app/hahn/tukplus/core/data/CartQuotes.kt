package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartRules
import app.hahn.tukplus.core.model.CommerceDelivery
import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.model.Workflow
import app.hahn.tukplus.core.pricing.CartPricing
import app.hahn.tukplus.core.pricing.Fleets
import app.hahn.tukplus.core.pricing.Pricing
import app.hahn.tukplus.core.pricing.Quote
import app.hahn.tukplus.core.pricing.ShopSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock

/** The cart with its price quote. [quote] is null while the shop settings are not loaded. */
data class CartQuote(
    val cart: Cart,
    /** The order type in use: the user's choice, else the first that the shop offers. */
    val fulfilment: String,
    /** The order types that the shop offers, in cart screen order. */
    val fulfilmentOptions: List<String>,
    val quote: Quote?,
    /** The Commerce workflow that the quote used (from the menu), or null. */
    val workflow: Workflow? = null,
    /** The delivery fleets that the quote used, or null. */
    val fleet: CommerceDelivery? = null,
    /** The shop location. */
    val pickup: LatLon? = null,
    /** The settings that the quote used. */
    val shop: ShopSettings? = null,
)

/**
 * Prices the cart with `core:pricing` (docs/pricing.md) before checkout: no address yet, so
 * there is no delivery fee. The shop settings come from the cached menu (the Commerce
 * workflow), the fleets from the cached delivery fleet. The quote is new each minute, because
 * the peak hours and cash hours depend on the time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CartQuotes(
    store: CartStore,
    private val shops: ShopRepository,
    scope: CoroutineScope,
    private val clock: Clock,
) {
    private val workflow = store.cart.map { it?.businessId }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            shops.menu(id).also { it.start() }.state.map { cached -> cached.data?.firstOrNull { it.workflowTypeName == Workflow.TYPE_COMMERCE } }
        }
    }.distinctUntilChanged()

    private val fleets = workflow.map { it?.id }.distinctUntilChanged().flatMapLatest { id ->
        if (id == null) flowOf(null) else shops.fleet(id).also { it.start() }.state.map { it.data }
    }

    private val minutes = flow {
        while (true) {
            emit(clock.millis())
            delay(60_000)
        }
    }

    val state: StateFlow<CartQuote?> = combine(store.cart, workflow, fleets, minutes) { cart, wf, fleet, now ->
        cart?.let { quote(it, wf, fleet, now) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private fun quote(cart: Cart, workflow: Workflow?, fleet: CommerceDelivery?, now: Long): CartQuote {
        // The listed shop has the same Commerce settings; use it until the menu is loaded.
        val business = shops.listedBusiness(cart.businessId)
        val data = workflow?.data ?: business?.commerceWorkflow?.data
        val offered = data?.fulfilmentOptions
        val fulfilment = CartRules.fulfilment(cart, offered)
        val options = CartRules.FULFILMENT_ORDER.filter { it in offered.orEmpty() }.ifEmpty { listOf(CartRules.FULFILMENT_ORDER.first()) }
        val pickup = business?.let { b -> if (b.lat != null && b.lon != null) LatLon(b.lat!!, b.lon!!) else null }
        val shop = data?.let(ShopSettings::from)
        val quote = shop?.let {
            Pricing.quote(
                CartPricing.request(
                    cart.copy(fulfilment = fulfilment),
                    it,
                    nowEpochMs = now,
                    fleets = fleet?.let(Fleets::from),
                    pickup = pickup,
                ),
            )
        }
        return CartQuote(cart, fulfilment, options, quote, workflow, fleet, pickup, shop)
    }
}
