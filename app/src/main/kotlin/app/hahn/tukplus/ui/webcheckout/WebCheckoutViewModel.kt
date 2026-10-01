package app.hahn.tukplus.ui.webcheckout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.hahn.tukplus.core.data.CartQuotes
import app.hahn.tukplus.core.data.Handoff
import app.hahn.tukplus.core.data.WebCheckout
import app.hahn.tukplus.core.model.TukJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import javax.inject.Inject

/**
 * One hand-off from our cart to the Tuk web checkout (PLAN.md §8a). The hand-off starts
 * when the screen opens, so each visit has a new one-time token.
 */
@HiltViewModel
class WebCheckoutViewModel @Inject constructor(
    application: Application,
    quotes: CartQuotes,
    private val web: WebCheckout,
    private val clock: Clock,
) : AndroidViewModel(application) {
    val handoff: Handoff? = quotes.state.value?.let { web.start(it, clock.millis()) }

    /** The document-start script with the hand-off values. Null when there is no cart. */
    val script: String? = handoff?.let { h ->
        val template = application.assets.open("web_checkout.js").bufferedReader().use { it.readText() }
        val values = buildJsonObject {
            put("token", h.token)
            put("values", h.storeValues)
        }
        // The JSON is a JavaScript object literal. "</" cannot end a script here, but keep it safe anyway.
        template.replace("__HANDOFF__", TukJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), values).replace("</", "<\\/"))
    }

    val url: String? = handoff?.let { "https://tukapp.co/shop/${it.businessId}" }

    private var sent = false
    private val _placed = MutableStateFlow(false)

    /** True when the web app placed the order and went to its orders list. */
    val placed: StateFlow<Boolean> = _placed.asStateFlow()

    fun onMessage(text: String) {
        val h = handoff ?: return
        if (text.contains("\"transaction_sent\"")) sent = true
        web.onMessage(h.token, text, clock.millis())
    }

    /** The web app changed its page. After it sent the order, its orders list means "placed". */
    fun onUrl(url: String) {
        val h = handoff ?: return
        if (sent && !_placed.value && url.startsWith("https://tukapp.co/orders")) {
            web.onOrderPlaced(h.token)
            _placed.value = true
        }
    }

    /** The WebView size in pixels, to compare with the page sizes in the `layout` log lines. */
    fun onViewSize(width: Int, height: Int, density: Float) {
        handoff?.let { web.onViewSize(it.token, width, height, density) }
    }

    fun onRenderGone(crashed: Boolean) {
        handoff?.let { web.onRenderGone(it.token, crashed) }
    }

    override fun onCleared() {
        handoff?.let { web.onClosed(it.token, _placed.value) }
    }
}
