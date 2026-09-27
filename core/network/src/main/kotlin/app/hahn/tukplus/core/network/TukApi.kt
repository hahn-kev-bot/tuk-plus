package app.hahn.tukplus.core.network

import app.hahn.tukplus.core.logging.Redactor
import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.model.Blob
import app.hahn.tukplus.core.model.Business
import app.hahn.tukplus.core.model.BusinessAutocomplete
import app.hahn.tukplus.core.model.CommerceDelivery
import app.hahn.tukplus.core.model.Directions
import app.hahn.tukplus.core.model.ForYouScore
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
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A point as the API wants it: "lat,lon". */
data class LatLon(val lat: Double, val lon: Double) {
    override fun toString(): String = "$lat,$lon"

    companion object {
        /** Centre of Chiang Mai, the only region of release 1 (PLAN.md §1). */
        val CHIANG_MAI = LatLon(18.796143, 98.979263)
    }
}

/**
 * Client for the Tuk API (api-reference.md). Only read calls for now.
 *
 * All calls run on [Dispatchers.IO] and give an [ApiResult]; they do not throw.
 */
class TukApi(
    private val client: OkHttpClient,
    val baseUrl: String = BASE_URL,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called with each raw response (URL, HTTP status, body). Tools use it to record fixtures. */
    private val bodyTap: ((HttpUrl, Int, String) -> Unit)? = null,
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    // ---- General

    /** The backend build, for example "3.7058f". */
    suspend fun version(): ApiResult<String> = getText(url("version"))

    // ---- Home and Eat (api-reference §4)

    suspend fun pageBlobs(page: String, region: String = REGION_CHIANG_MAI): ApiResult<List<Blob>> =
        getList(url("blob/active") { addQueryParameter("type", "page"); addQueryParameter("page", page); addQueryParameter("region", region) }, Blob.serializer())

    // ---- Eateries (api-reference §5)

    suspend fun eateries(coords: LatLon = LatLon.CHIANG_MAI): ApiResult<List<Business>> =
        getList(url("businesses") { addQueryParameter("type", "eatery"); addQueryParameter("coords", coords.toString()); cacheBuster() }, Business.serializer())

    suspend fun newShops(coords: LatLon = LatLon.CHIANG_MAI): ApiResult<List<NewShop>> =
        getList(url("businesses") { addQueryParameter("type", "new_shops"); addQueryParameter("coords", coords.toString()); cacheBuster() }, NewShop.serializer())

    suspend fun forYou(userId: String?): ApiResult<List<ForYouScore>> =
        getList(url("recommendations/foryou") { addQueryParameter("page", "eat"); addQueryParameter("user_id", userId.orEmpty()); cacheBuster() }, ForYouScore.serializer())

    suspend fun business(businessId: String): ApiResult<Business> =
        getJson(url("businesses/$businessId") { cacheBuster() }, Business.serializer())

    /**
     * The server's open check. It answers "yes" with HTTP 200. A closed shop gives
     * HTTP 500 with a reason that starts with "checkCommerceAllowTransaction:"; that
     * is a normal answer, not a failure.
     */
    suspend fun shopOpen(businessId: String): ApiResult<ShopOpenStatus> =
        when (val result = getText(url("helpers/shop_open") { addQueryParameter("id", businessId); cacheBuster() })) {
            is ApiResult.Success -> result.map(ShopOpenStatus::fromText)
            is ApiResult.HttpError ->
                if (result.code == 500 && result.text.startsWith(ShopOpenStatus.CLOSED_PREFIX)) {
                    ApiResult.Success(ShopOpenStatus.fromText(result.text), 0)
                } else {
                    result
                }
            is ApiResult.Failure -> result
        }

    suspend fun shortLink(handle: String): ApiResult<ShortLink> =
        getJson(url("short_link/${if (handle.startsWith("@")) handle else "@$handle"}"), ShortLink.serializer())

    // ---- Search (api-reference §5.4)

    suspend fun autocompleteBusiness(text: String): ApiResult<BusinessAutocomplete> =
        getJson(url("autocomplete") { addQueryParameter("business", text) }, BusinessAutocomplete.serializer(), emptyValue = BusinessAutocomplete())

    suspend fun searchMenuItems(text: String): ApiResult<List<MenuItemSearchHit>> =
        getList(url("search/menu_items") { addQueryParameter("text", text) }, MenuItemSearchHit.serializer())

    // ---- Shop and menu (api-reference §6)

    /** The workflows of a shop, with all menu blobs. */
    suspend fun workflowsForBusiness(businessId: String): ApiResult<List<Workflow>> =
        getList(url("workflows/$businessId") { cacheBuster() }, Workflow.serializer())

    suspend fun commerceDelivery(commerceWorkflowId: String): ApiResult<CommerceDelivery> =
        getJson(url("workflows") { addQueryParameter("commerce_delivery", commerceWorkflowId); cacheBuster() }, CommerceDelivery.serializer())

    // ---- Distance and price (api-reference §7)

    suspend fun directions(origin: LatLon, destination: LatLon): ApiResult<Directions> =
        getJson(url("directions") { addQueryParameter("origin", origin.toString()); addQueryParameter("destination", destination.toString()) }, Directions.serializer())

    suspend fun priceCheck(pickup: LatLon, dropoff: LatLon): ApiResult<PriceCheck> =
        getJson(url("delivery/price_check") { addQueryParameter("pickup", pickup.toString()); addQueryParameter("dropoff", dropoff.toString()); addQueryParameter("type", "express") }, PriceCheck.serializer())

    /** The raw body of a GET, for tools that record fixtures. */
    suspend fun getRaw(pathAndQuery: String): ApiResult<String> = getText((baseUrl + pathAndQuery).toHttpUrl())

    // ---- Helpers

    private fun url(path: String, build: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        base.newBuilder().addPathSegments(path).apply(build).build()

    private fun HttpUrl.Builder.cacheBuster() {
        addQueryParameter("ts", clock().toString())
    }

    private suspend fun getText(url: HttpUrl): ApiResult<String> = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        try {
            client.newCall(Request.Builder().url(url).get().build()).executeAsync().use { response ->
                val body = response.body.string()
                val ms = (System.nanoTime() - start) / 1_000_000
                bodyTap?.invoke(url, response.code, body)
                if (response.isSuccessful) ApiResult.Success(body, ms)
                else ApiResult.HttpError(response.code, body.take(ERROR_TEXT))
            }
        } catch (e: IOException) {
            ApiResult.NetworkError(e)
        }
    }

    private suspend fun <T> getJson(
        url: HttpUrl,
        serializer: DeserializationStrategy<T>,
        emptyValue: T? = null,
    ): ApiResult<T> = when (val text = getText(url)) {
        is ApiResult.Failure -> text
        is ApiResult.Success -> decode(text, serializer, emptyValue)
    }

    private suspend fun <T> getList(url: HttpUrl, item: kotlinx.serialization.KSerializer<T>): ApiResult<List<T>> =
        getJson(url, ListSerializer(item), emptyValue = emptyList())

    private fun <T> decode(text: ApiResult.Success<String>, serializer: DeserializationStrategy<T>, emptyValue: T?): ApiResult<T> {
        val body = text.value.trim()
        // The API sends "null" for an empty result (api-reference §1).
        if ((body == "null" || body.isEmpty()) && emptyValue != null) return ApiResult.Success(emptyValue, text.durationMs)
        return try {
            ApiResult.Success(TukJson.decodeFromString(serializer, body), text.durationMs)
        } catch (e: Exception) {
            ApiResult.ParseError(e, body.take(ERROR_TEXT))
        }
    }

    companion object {
        const val BASE_URL = "https://api.tukbot.com/prod/tuk/"
        const val REGION_CHIANG_MAI = "Chiang Mai"
        private const val ERROR_TEXT = 4 * 1024

        /** Home rows (api-reference §4). */
        val HOME_PAGES: List<String> = (1..12).map { "Home_$it" } + "Home_Business"
        const val EAT_PAGE = "Eat"

        /**
         * The OkHttp client with auth, logging and retries. One client for the whole app,
         * so that the HTTP/2 connection is shared.
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
