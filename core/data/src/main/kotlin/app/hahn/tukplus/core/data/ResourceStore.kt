package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.network.ApiResult
import app.hahn.tukplus.core.network.Endpoint
import kotlinx.coroutines.CoroutineScope
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one [CachedResource] per cache key, so that all screens share the same data
 * and the same running request.
 */
class ResourceStore(
    private val cache: ResponseCache,
    private val fetch: suspend (Endpoint<*>) -> ApiResult<String>,
    private val scope: CoroutineScope,
    val clock: Clock,
    private val log: TukLog,
) {
    private val resources = ConcurrentHashMap<String, CachedResource<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> resource(
        endpoint: Endpoint<T>,
        policy: CachePolicy,
        isOutdated: (T) -> Boolean = { false },
    ): CachedResource<T> = resources.getOrPut(endpoint.cacheKey) {
        CachedResource(endpoint, policy, cache, fetch, scope, clock, log, isOutdated = isOutdated)
    } as CachedResource<T>
}
