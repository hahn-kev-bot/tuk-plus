package app.hahn.tukplus.core.data

import app.hahn.tukplus.core.logging.TukLog
import app.hahn.tukplus.core.network.ApiResult
import app.hahn.tukplus.core.network.Endpoint
import app.hahn.tukplus.core.network.TukApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachedResourceTest {
    private val endpoints = TukApi(OkHttpClient()).endpoints
    private var now: Instant = Instant.parse("2026-09-27T08:00:00Z")
    private val clock = object : Clock() {
        override fun instant(): Instant = now
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
    }
    private val cache = MemoryResponseCache()
    private var calls = 0
    private var answer: suspend () -> ApiResult<String> = { ApiResult.Success("3.1", 5) }
    private val fetch: suspend (Endpoint<*>) -> ApiResult<String> = { calls++; answer() }

    private fun TestScope.resource(policy: CachePolicy = CachePolicy(Duration.ofMinutes(30))) =
        CachedResource(endpoints.version(), policy, cache, fetch, backgroundScope, clock, TukLog.NONE)

    @Test
    fun `no cache - loads from the network and stores the body`() = runTest {
        val r = resource()
        val result = r.get()
        assertEquals("3.1", result.data)
        assertEquals(Cached.Status.Fresh, result.status)
        assertEquals(now, result.fetchedAt)
        assertEquals("3.1", cache.read("version")!!.text)
        assertEquals(1, calls)
    }

    @Test
    fun `fresh cache - no network call`() = runTest {
        cache.write("version", CachedBody("3.0", now.minus(Duration.ofMinutes(10))))
        val r = resource()
        val result = r.get()
        assertEquals("3.0", result.data)
        assertEquals(Cached.Status.Fresh, result.status)
        assertEquals(0, calls)
    }

    @Test
    fun `stale cache - shows old data first, then the new data`() = runTest {
        cache.write("version", CachedBody("3.0", now.minus(Duration.ofHours(2))))
        val gate = CompletableDeferred<Unit>()
        answer = { gate.await(); ApiResult.Success("3.1", 5) }
        val r = resource()
        r.start()
        runCurrent()
        assertEquals("3.0", r.state.value.data)
        assertEquals(Cached.Status.Refreshing, r.state.value.status)
        gate.complete(Unit)
        runCurrent()
        assertEquals("3.1", r.state.value.data)
        assertEquals(Cached.Status.Fresh, r.state.value.status)
    }

    @Test
    fun `failure keeps the old data and says offline`() = runTest {
        cache.write("version", CachedBody("3.0", now.minus(Duration.ofHours(2))))
        answer = { ApiResult.NetworkError(IOException("no route")) }
        val r = resource()
        val result = r.get()
        assertEquals("3.0", result.data)
        val status = assertIs<Cached.Status.Error>(result.status)
        assertTrue(status.offline)
        assertEquals(now.minus(Duration.ofHours(2)), result.fetchedAt)
    }

    @Test
    fun `failure without cache has no data`() = runTest {
        answer = { ApiResult.HttpError(500, "sql: no rows in result set") }
        val result = resource().get()
        assertNull(result.data)
        assertEquals(false, assertIs<Cached.Status.Error>(result.status).offline)
    }

    @Test
    fun `parallel callers share one request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        answer = { gate.await(); ApiResult.Success("3.1", 5) }
        val r = resource()
        val a = async { r.get() }
        val b = async { r.get() }
        advanceUntilIdle()
        gate.complete(Unit)
        assertEquals("3.1", a.await().data)
        assertEquals("3.1", b.await().data)
        assertEquals(1, calls)
    }

    @Test
    fun `refresh always calls the network`() = runTest {
        val r = resource()
        r.get()
        r.refresh().await()
        assertEquals(2, calls)
    }

    @Test
    fun `a body that cannot be read is an error, and the cache is not changed`() = runTest {
        val endpoint = endpoints.eateries()
        cache.write(endpoint.cacheKey, CachedBody("[]", now.minus(Duration.ofHours(1))))
        answer = { ApiResult.Success("<html>", 5) }
        val r = CachedResource(endpoint, CachePolicy(Duration.ofMinutes(15)), cache, fetch, backgroundScope, clock, TukLog.NONE)
        val result = r.get()
        assertEquals(emptyList(), result.data)
        assertIs<Cached.Status.Error>(result.status)
        assertEquals("[]", cache.read(endpoint.cacheKey)!!.text)
    }

    @Test
    fun `empty result uses the longer refresh age`() = runTest {
        val endpoint = endpoints.pageBlobs("Home_9")
        cache.write(endpoint.cacheKey, CachedBody("null", now.minus(Duration.ofHours(3))))
        val r = CachedResource(endpoint, Policies.HOME_PAGE, cache, fetch, backgroundScope, clock, TukLog.NONE)
        assertEquals(emptyList(), r.get().data)
        assertEquals(0, calls) // 3 hours < 1 day for an empty page
        now = now.plus(Duration.ofDays(1))
        assertTrue(r.needsRefresh())
    }

    @Test
    fun `outdated rule forces a refresh`() = runTest {
        cache.write("version", CachedBody("3.0", now))
        val r = CachedResource(endpoints.version(), CachePolicy(Duration.ofHours(1)), cache, fetch, backgroundScope, clock, TukLog.NONE, isOutdated = { it == "3.0" })
        assertEquals("3.1", r.get().data)
        assertEquals(1, calls)
    }
}
