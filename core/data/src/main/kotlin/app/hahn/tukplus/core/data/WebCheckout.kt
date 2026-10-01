package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.domain.Cart
import app.hahn.tukplus.core.domain.CartStoreJson
import app.hahn.tukplus.core.logging.LogLevel
import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.CommerceDelivery
import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.WorkflowData
import app.hahn.tukplus.core.pricing.CartPricing
import app.hahn.tukplus.core.pricing.Fleets
import app.hahn.tukplus.core.pricing.HandoffSnapshot
import app.hahn.tukplus.core.pricing.OrderCheck
import app.hahn.tukplus.core.pricing.ShopSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.UUID

/** What the app saves for one hand-off to the web checkout (PLAN.md §8a). */
@Serializable
data class HandoffRecord(
    val token: String,
    val startedAt: Long,
    val cart: Cart,
    /** The Commerce `workflow.data` that our price code used. */
    val workflowData: JsonElement? = null,
    /** The delivery fleets (`workflows?commerce_delivery=`). */
    val fleet: JsonElement? = null,
    val pickupLat: Double? = null,
    val pickupLon: Double? = null,
    /** Epoch ms when the web app sent `POST transactions`, if it did. */
    val sentAt: Long? = null,
    val status: Int? = null,
    /** The `POST transactions` request body as the web app sent it. */
    val request: String? = null,
    val response: String? = null,
)

/** The values for the document-start script: the one-time token and the store keys to merge. */
data class Handoff(val token: String, val businessId: String, val storeValues: JsonObject)

/**
 * The web checkout fallback (PLAN.md §8a): makes the web basket from our cart, keeps a
 * snapshot of our pricing inputs, takes the `POST transactions` that the web app sends, and
 * runs the background order check (core:pricing `OrderCheck`).
 *
 * Each hand-off is one file in [dir], so the support logs can be compared with it later.
 * Only the newest 20 files are kept.
 */
class WebCheckout(
    private val dir: File,
    private val cartStore: CartStore,
    private val scope: CoroutineScope,
    private val redactor: Redactor,
    private val log: TukLog,
) {
    /** Starts a hand-off for the cart in [quote]. Null when there is no cart. */
    fun start(quote: CartQuote, now: Long): Handoff? {
        val cart = quote.cart.copy(fulfilment = quote.fulfilment)
        if (cart.isEmpty) return null
        val shop = quote.shop ?: ShopSettings()
        val token = UUID.randomUUID().toString()
        val record = HandoffRecord(
            token = token,
            startedAt = now,
            cart = cart,
            workflowData = quote.workflow?.data?.let { TukJson.encodeToJsonElement(WorkflowData.serializer(), it) },
            fleet = quote.fleet?.let { TukJson.encodeToJsonElement(CommerceDelivery.serializer(), it) },
            pickupLat = quote.pickup?.lat,
            pickupLon = quote.pickup?.lon,
        )
        save(record)
        val values = buildJsonObject {
            put("basket", basket(cart, shop, now))
            put("language", "en")
            put("region", "Chiang Mai")
        }
        log.i(
            "web_checkout", "start",
            "business_id" to cart.businessId,
            "lines" to cart.lines.size,
            "items" to cart.itemCount,
            "fulfilment" to cart.fulfilment,
            "our_total" to quote.quote?.total,
            "token" to token.take(8),
        )
        return Handoff(token, cart.businessId, values)
    }

    /** A message from the document-start script (JSON text). */
    fun onMessage(token: String, text: String, now: Long) {
        val message = runCatching { TukJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
        val type = (message?.get("type") as? JsonPrimitive)?.content
        when (type) {
            "injected" -> log.i("web_checkout", "injected", "token" to token.take(8), "keys" to message["keys"]?.toString())
            "inject_skipped" -> log.i("web_checkout", "inject_skipped", "token" to token.take(8))
            "layout" -> log.i("web_checkout", "layout", "token" to token.take(8), "values" to message.filterKeys { it != "type" }.toString())
            "transaction_sent" -> log.i("web_checkout", "order_sent", "token" to token.take(8), "req" to message.string("request")?.let { redactor.redactBody(it, MAX_BODY) })
            "transaction" -> onTransaction(token, message, now)
            else -> log.w("web_checkout", "message", "token" to token.take(8), "text" to text.take(300))
        }
    }

    /** The web app went to its orders list: the order is placed. Our cart is cleared. */
    fun onOrderPlaced(token: String) {
        log.i("web_checkout", "order_placed", "token" to token.take(8))
        cartStore.clear()
    }

    fun onViewSize(token: String, width: Int, height: Int, density: Float) {
        log.i("web_checkout", "view_size", "token" to token.take(8), "px" to "${width}x$height", "dp" to "${(width / density).toInt()}x${(height / density).toInt()}")
    }

    fun onRenderGone(token: String, crashed: Boolean) {
        log.w("web_checkout", "render_gone", "token" to token.take(8), "crashed" to crashed)
    }

    fun onClosed(token: String, placed: Boolean) {
        log.i("web_checkout", "closed", "token" to token.take(8), "placed" to placed)
    }

    private fun onTransaction(token: String, message: JsonObject, now: Long) {
        val status = (message["status"] as? JsonPrimitive)?.content?.toIntOrNull()
        val request = message.string("request")
        val response = message.string("response")
        log.i(
            "web_checkout", "order_response",
            "token" to token.take(8),
            "status" to status,
            "req" to request?.let { redactor.redactBody(it, MAX_BODY) },
            "resp" to response?.let { redactor.redactBody(it, MAX_BODY) },
        )
        val record = read(token)?.copy(sentAt = now, status = status, request = request, response = response) ?: return
        save(record)
        if (status == null || status !in 200..299 || request == null) return
        scope.launch(Dispatchers.Default) { check(record, request, now) }
    }

    private fun check(record: HandoffRecord, request: String, sentAt: Long) {
        try {
            val body = TukJson.parseToJsonElement(request) as? JsonObject ?: return
            val data = record.workflowData?.let { TukJson.decodeFromJsonElement(WorkflowData.serializer(), it) }
            val fleet = record.fleet?.let { TukJson.decodeFromJsonElement(CommerceDelivery.serializer(), it) }
            val pickup = if (record.pickupLat != null && record.pickupLon != null) LatLon(record.pickupLat, record.pickupLon) else null
            val snapshot = HandoffSnapshot(record.cart, ShopSettings.from(data), fleet?.let(Fleets::from), pickup)
            val result = OrderCheck.check(snapshot, body, sentAt)
            log.log(
                if (result.matches) LogLevel.INFO else LogLevel.WARN,
                "web_checkout", "check",
                mapOf(
                    "token" to record.token.take(8),
                    "result" to if (result.matches) "match" else "differ",
                    "not_priced" to result.notPriced,
                    "amount_diffs" to result.amountDiffs.joinToString("; ") { "${it.path}: ours=${it.ours} web=${it.web}" }.ifEmpty { null },
                    "basket_diffs" to result.basketDiffs.joinToString("; ") { "${it.path}: ours=${it.ours} web=${it.web}" }.ifEmpty { null },
                ),
            )
        } catch (e: Exception) {
            log.e("web_checkout", "check_failed", "error" to e.toString())
        }
    }

    /**
     * The web basket (`state.basket`, docs/pricing.md §16). The web app has quantity 1 on each
     * line with option groups, so such a line with quantity n becomes n lines.
     */
    private fun basket(cart: Cart, shop: ShopSettings, now: Long): JsonObject {
        val items = cart.lines.flatMap { line ->
            val item = CartPricing.webItem(line, shop)
            if ("id2" in item && line.quantity > 1) {
                (1..line.quantity).map { n -> line(JsonObject(item + ("id2" to JsonPrimitive("${line.lineId}-$n"))), 1) }
            } else {
                listOf(line(item, line.quantity))
            }
        }
        return buildJsonObject {
            put("shop_id", cart.businessId)
            put("created_at", Instant.ofEpochMilli(now).toString())
            put("notes", cart.notes)
            put("items", JsonArray(items))
        }
    }

    private fun line(item: JsonObject, quantity: Int) = buildJsonObject {
        put("item", item)
        put("quantity", quantity)
    }

    private fun file(token: String) = File(dir, "$token.json")

    private fun read(token: String): HandoffRecord? =
        runCatching { CartStoreJson.decodeFromString(HandoffRecord.serializer(), file(token).readText()) }.getOrNull()

    private fun save(record: HandoffRecord) {
        runCatching {
            dir.mkdirs()
            file(record.token).writeText(CartStoreJson.encodeToString(HandoffRecord.serializer(), record))
            dir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        }.onFailure { log.e("web_checkout", "save_failed", "error" to it.toString()) }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        const val MAX_BODY = 64 * 1024
        const val KEEP = 20
    }
}
