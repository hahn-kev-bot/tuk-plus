package app.hahn.tukplus.core.network

import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.Blob
import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.model.BusinessAutocomplete
import app.hahn.tukplus.core.model.CommerceDelivery
import app.hahn.tukplus.core.model.Directions
import app.hahn.tukplus.core.model.ForYouScore
import app.hahn.tukplus.core.model.LatLon
import app.hahn.tukplus.core.model.MenuItemSearchHit
import app.hahn.tukplus.core.model.NewShop
import app.hahn.tukplus.core.model.PriceCheck
import app.hahn.tukplus.core.model.ShopOpenStatus
import app.hahn.tukplus.core.model.ShortLink
import app.hahn.tukplus.core.model.TukJson
import app.hahn.tukplus.core.model.Workflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One API call: its URL, its cache key, and how to read its body.
 *
 * The cache stores the raw body text under [cacheKey] and reads it again with [decode].
 * So a cached value is parsed by the same code as a fresh one.
 */
class Endpoint<T> internal constructor(
    /** Stable key for the local cache, for example "page:Home_1" or "menu:<businessId>". */
    val cacheKey: String,
    internal val url: HttpUrl,
    /** Add `ts=<now>` like the web app does. The value is not part of [cacheKey]. */
    internal val cacheBuster: Boolean,
    /** Some error answers are normal answers (for example a closed shop). */
    internal val acceptError: (code: Int, text: String) -> Boolean = { _, _ -> false },
    private val parse: (String) -> T,
) {
    /** Reads a body. Gives [ApiResult.ParseError] when the body does not match. */
    fun decode(text: String, durationMs: Long = 0): ApiResult<T> = try {
        ApiResult.Success(parse(text), durationMs)
    } catch (e: Exception) {
        ApiResult.ParseError(e, text.take(4 * 1024))
    }

    override fun toString(): String = "Endpoint($cacheKey)"
}

/**
 * Client for the Tuk API (api-reference.md). Only read calls for now.
 *
 * Use [endpoints] with [fetch] (typed result) or [fetchText] (raw body, for the cache).
 * The named functions (for example [eateries]) are short forms of the same calls.
 * All calls run on [Dispatchers.IO] and give an [ApiResult]; they do not throw.
 */
class TukApi(
    private val client: OkHttpClient,
    val baseUrl: String = BASE_URL,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called with each raw response (URL, HTTP status, body). Tools use it to record fixtures. */
    private val bodyTap: ((HttpUrl, Int, String) -> Unit)? = null,
) {
    val endpoints = Endpoints(baseUrl.toHttpUrl())

    /** Calls the endpoint and gives the raw body. */
    suspend fun fetchText(endpoint: Endpoint<*>): ApiResult<String> = withContext(Dispatchers.IO) {
        val url = if (endpoint.cacheBuster) {
            endpoint.url.newBuilder().addQueryParameter("ts", clock().toString()).build()
        } else {
            endpoint.url
        }
        val start = System.nanoTime()
        try {
            client.newCall(Request.Builder().url(url).get().build()).executeAsync().use { response ->
                val body = response.body.string()
                val ms = (System.nanoTime() - start) / 1_000_000
                bodyTap?.invoke(url, response.code, body)
                when {
                    response.isSuccessful -> ApiResult.Success(body, ms)
                    endpoint.acceptError(response.code, body) -> ApiResult.Success(body, ms)
                    else -> ApiResult.HttpError(response.code, body.take(ERROR_TEXT))
                }
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        }
    }

    /** Calls the endpoint and reads the body. */
    suspend fun <T> fetch(endpoint: Endpoint<T>): ApiResult<T> = when (val text = fetchText(endpoint)) {
        is ApiResult.Failure -> text
        is ApiResult.Success -> endpoint.decode(text.value, text.durationMs)
    }

    // ---- Short forms

    suspend fun version() = fetch(endpoints.version())
    suspend fun pageBlobs(page: String, region: String = REGION_CHIANG_MAI) = fetch(endpoints.pageBlobs(page, region))
    suspend fun eateries(coords: LatLon = LatLon.CHIANG_MAI) = fetch(endpoints.eateries(coords))
    suspend fun newShops(coords: LatLon = LatLon.CHIANG_MAI) = fetch(endpoints.newShops(coords))
    suspend fun forYou(userId: String?) = fetch(endpoints.forYou(userId))
    suspend fun business(businessId: String) = fetch(endpoints.business(businessId))
    suspend fun shopOpen(businessId: String) = fetch(endpoints.shopOpen(businessId))
    suspend fun shortLink(handle: String) = fetch(endpoints.shortLink(handle))
    suspend fun autocompleteBusiness(text: String) = fetch(endpoints.autocompleteBusiness(text))
    suspend fun searchMenuItems(text: String) = fetch(endpoints.searchMenuItems(text))
    suspend fun workflowsForBusiness(businessId: String) = fetch(endpoints.workflowsForBusiness(businessId))
    suspend fun commerceDelivery(commerceWorkflowId: String) = fetch(endpoints.commerceDelivery(commerceWorkflowId))
    suspend fun directions(origin: LatLon, destination: LatLon) = fetch(endpoints.directions(origin, destination))
    suspend fun priceCheck(pickup: LatLon, dropoff: LatLon) = fetch(endpoints.priceCheck(pickup, dropoff))

    companion object {
        const val BASE_URL = "https://api.tukbot.com/prod/tuk/"
        const val REGION_CHIANG_MAI = "Chiang Mai"
        private const val ERROR_TEXT = 4 * 1024

        /** Home rows (api-reference §4). */
        val HOME_PAGES: List<String> = (1..12).map { "Home_$it" } + "Home_Business"
        const val EAT_PAGE = "Eat"

        /**
         * The OkHttp client with auth, logging and retries. One client for the whole app,
         * so that the HTTP/2 connection is shared (a reused connection saves 0.5–0.7 s per call).
         */
        fun createClient(
            deviceUuid: () -> String,
            userAgent: String,
            log: TukLog,
            redactor: Redactor,
            baseUrl: String = BASE_URL,
            retry: RetryInterceptor = RetryInterceptor(),
        ): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(deviceUuid, userAgent))
            .addInterceptor(HttpLogInterceptor(log, redactor, baseUrl))
            .addInterceptor(retry)
            .build()
    }
}

/** All read endpoints (api-reference.md). */
class Endpoints internal constructor(private val base: HttpUrl) {

    fun version() = Endpoint("version", url("version"), cacheBuster = false) { it.trim() }

    // ---- Home and Eat (api-reference §4)

    fun pageBlobs(page: String, region: String = TukApi.REGION_CHIANG_MAI) = Endpoint(
        "page:$region:$page",
        url("blob/active") { q("type", "page"); q("page", page); q("region", region) },
        cacheBuster = false,
        parse = list(Blob.serializer()),
    )

    // ---- Eateries (api-reference §5)

    fun eateries(coords: LatLon = LatLon.CHIANG_MAI) = Endpoint(
        "eateries:$coords",
        url("businesses") { q("type", "eatery"); q("coords", coords.toString()) },
        cacheBuster = true,
        parse = list(Business.serializer()),
    )

    fun newShops(coords: LatLon = LatLon.CHIANG_MAI) = Endpoint(
        "new_shops:$coords",
        url("businesses") { q("type", "new_shops"); q("coords", coords.toString()) },
        cacheBuster = true,
        parse = list(NewShop.serializer()),
    )

    fun forYou(userId: String?) = Endpoint(
        "foryou:${userId.orEmpty()}",
        url("recommendations/foryou") { q("page", "eat"); q("user_id", userId.orEmpty()) },
        cacheBuster = true,
        parse = list(ForYouScore.serializer()),
    )

    fun business(businessId: String) = Endpoint(
        "business:$businessId", url("businesses/$businessId"), cacheBuster = true, parse = json(Business.serializer()),
    )

    /**
     * The server's open check. It answers "yes" with HTTP 200. A closed shop gives HTTP 500
     * with a reason that starts with "checkCommerceAllowTransaction:"; that is a normal answer.
     */
    fun shopOpen(businessId: String) = Endpoint(
        "shop_open:$businessId",
        url("helpers/shop_open") { q("id", businessId) },
        cacheBuster = true,
        acceptError = { code, text -> code == 500 && text.trim().startsWith(ShopOpenStatus.CLOSED_PREFIX) },
        parse = ShopOpenStatus::fromText,
    )

    fun shortLink(handle: String): Endpoint<ShortLink> {
        val normalized = if (handle.startsWith("@")) handle else "@$handle"
        return Endpoint("short_link:$normalized", url("short_link/$normalized"), cacheBuster = false, parse = json(ShortLink.serializer()))
    }

    // ---- Search (api-reference §5.4)

    fun autocompleteBusiness(text: String) = Endpoint(
        "autocomplete_business:${text.lowercase()}",
        url("autocomplete") { q("business", text) },
        cacheBuster = false,
        parse = json(BusinessAutocomplete.serializer(), emptyValue = BusinessAutocomplete()),
    )

    fun searchMenuItems(text: String) = Endpoint(
        "menu_items:${text.lowercase()}",
        url("search/menu_items") { q("text", text) },
        cacheBuster = false,
        parse = list(MenuItemSearchHit.serializer()),
    )

    // ---- Shop and menu (api-reference §6)

    /** The workflows of a shop, with all menu blobs. */
    fun workflowsForBusiness(businessId: String) = Endpoint(
        "menu:$businessId", url("workflows/$businessId"), cacheBuster = true, parse = list(Workflow.serializer()),
    )

    fun commerceDelivery(commerceWorkflowId: String) = Endpoint(
        "fleet:$commerceWorkflowId",
        url("workflows") { q("commerce_delivery", commerceWorkflowId) },
        cacheBuster = true,
        parse = json(CommerceDelivery.serializer()),
    )

    // ---- Distance and price (api-reference §7)

    fun directions(origin: LatLon, destination: LatLon) = Endpoint(
        "directions:$origin:$destination",
        url("directions") { q("origin", origin.toString()); q("destination", destination.toString()) },
        cacheBuster = false,
        parse = json(Directions.serializer()),
    )

    fun priceCheck(pickup: LatLon, dropoff: LatLon) = Endpoint(
        "price_check:$pickup:$dropoff",
        url("delivery/price_check") { q("pickup", pickup.toString()); q("dropoff", dropoff.toString()); q("type", "express") },
        cacheBuster = false,
        parse = json(PriceCheck.serializer()),
    )

    // ---- Helpers

    private fun url(path: String, build: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder().addPathSegments(path).apply(build).build()

    private fun HttpUrl.Builder.q(name: String, value: String) {
        addQueryParameter(name, value)
    }

    private fun <T> json(serializer: DeserializationStrategy<T>, emptyValue: T? = null): (String) -> T = { text ->
        val body = text.trim()
        // The API sends "null" for an empty result (api-reference §1).
        if ((body == "null" || body.isEmpty()) && emptyValue != null) emptyValue
        else TukJson.decodeFromString(serializer, body)
    }

    private fun <T> list(item: KSerializer<T>): (String) -> List<T> = json(ListSerializer(item), emptyValue = emptyList())
}
