package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.network.ApiResult
import app.hahn.tukplus.core.network.Endpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** How long cached data counts as fresh (PLAN.md §5.3). */
data class CachePolicy(
    val maxAge: Duration,
    /** Refresh age for an empty result. Empty pages change seldom, so check them less often. */
    val emptyMaxAge: Duration? = null,
)

/**
 * One API resource with cache-first loading (PLAN.md §5.1):
 *
 * 1. [state] first gives the cached data from disk, if there is any.
 * 2. If the data is older than its refresh age (or missing), a refresh starts in the background.
 * 3. While the refresh runs, the old data stays in [state] with [Cached.Status.Refreshing].
 * 4. On success the new data replaces it. On failure the old data stays, with [Cached.Status.Error].
 *
 * Only one request runs at a time for one resource.
 */
class CachedResource<T>(
    val endpoint: Endpoint<T>,
    private val policy: CachePolicy,
    private val cache: ResponseCache,
    private val fetch: suspend (Endpoint<*>) -> ApiResult<String>,
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val log: TukLog,
    private val isEmpty: (T) -> Boolean = { (it as? Collection<*>)?.isEmpty() == true },
    /** Extra rule: the cached data is too old for another reason (for example a newer menu version). */
    private val isOutdated: (T) -> Boolean = { false },
) {
    private val _state = MutableStateFlow(Cached.empty<T>())
    val state: StateFlow<Cached<T>> = _state.asStateFlow()

    private val loadMutex = Mutex()
    private var loadedFromDisk = false
    private val refreshMutex = Mutex()
    private var running: Deferred<Cached<T>>? = null

    /** Reads the disk cache (one time), then refreshes if the data is stale. Does not wait for the network. */
    fun start() {
        scope.async { ensureLoaded(); if (needsRefresh()) refreshAsync() }
    }

    /** Reads the disk cache (one time), refreshes if stale, and waits for the result. */
    suspend fun get(): Cached<T> {
        ensureLoaded()
        return if (needsRefresh()) refreshAsync().await() else _state.value
    }

    /** Starts a refresh now, also when the data is fresh (for example on pull-to-refresh). */
    fun refresh(): Deferred<Cached<T>> {
        return scope.async { ensureLoaded(); refreshAsync().await() }
    }

    /** True when the data is missing or older than its refresh age. */
    fun needsRefresh(now: Instant = clock.instant()): Boolean {
        val current = _state.value
        val data = current.data ?: return true
        val fetchedAt = current.fetchedAt ?: return true
        if (isOutdated(data)) return true
        val maxAge = if (isEmpty(data)) policy.emptyMaxAge ?: policy.maxAge else policy.maxAge
        return Duration.between(fetchedAt, now) >= maxAge
    }

    private suspend fun ensureLoaded() = loadMutex.withLock {
        if (loadedFromDisk) return@withLock
        loadedFromDisk = true
        val body = cache.read(endpoint.cacheKey)
        if (body == null) {
            log.d("cache", "miss", "key" to endpoint.cacheKey)
            return@withLock
        }
        when (val decoded = endpoint.decode(body.text)) {
            is ApiResult.Success -> {
                val age = Duration.between(body.fetchedAt, clock.instant())
                _state.value = Cached(decoded.value, body.fetchedAt, Cached.Status.Fresh)
                _state.value = _state.value.copy(status = if (needsRefresh()) Cached.Status.Stale else Cached.Status.Fresh)
                log.d("cache", "hit", "key" to endpoint.cacheKey, "age_s" to age.seconds, "stale" to (_state.value.status == Cached.Status.Stale))
            }
            is ApiResult.Failure -> log.w("cache", "unreadable", "key" to endpoint.cacheKey, "error" to decoded.message)
        }
    }

    private suspend fun refreshAsync(): Deferred<Cached<T>> = refreshMutex.withLock {
        running?.takeIf { it.isActive }?.let { return@withLock it }
        scope.async { doRefresh() }.also { running = it }
    }

    private suspend fun doRefresh(): Cached<T> {
        val before = _state.value
        _state.value = before.copy(status = Cached.Status.Refreshing)
        log.d("cache", "refresh_start", "key" to endpoint.cacheKey, "age_s" to before.age(clock.instant())?.seconds)
        val next: Cached<T> = when (val text = fetch(endpoint)) {
            is ApiResult.Failure -> failed(before, text)
            is ApiResult.Success -> when (val decoded = endpoint.decode(text.value, text.durationMs)) {
                is ApiResult.Failure -> failed(before, decoded)
                is ApiResult.Success -> {
                    val now = clock.instant()
                    cache.write(endpoint.cacheKey, CachedBody(text.value, now))
                    log.d("cache", "refresh_done", "key" to endpoint.cacheKey, "ms" to text.durationMs)
                    Cached(decoded.value, now, Cached.Status.Fresh)
                }
            }
        }
        _state.value = next
        return next
    }

    private fun failed(before: Cached<T>, failure: ApiResult.Failure): Cached<T> {
        log.w("cache", "refresh_failed", "key" to endpoint.cacheKey, "error" to failure.message)
        return before.copy(status = Cached.Status.Error(failure.message, offline = failure is ApiResult.NetworkError))
    }
}
