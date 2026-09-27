package app.hahn.tukplus.core.data

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheStorageTest {
    @Test
    fun `file cache writes, reads and trims`() = runTest {
        val dir = Files.createTempDirectory("cache").toFile()
        val cache = FileResponseCache(dir, maxBytes = 3_000)
        val t = Instant.parse("2026-09-27T08:00:00Z")
        cache.write("a", CachedBody("x".repeat(1_000) + "\nsecond line", t))
        assertEquals(CachedBody("x".repeat(1_000) + "\nsecond line", t), cache.read("a"))
        assertNull(cache.read("b"))
        cache.write("b", CachedBody("y".repeat(1_000), t))
        Thread.sleep(20) // lastModified has millisecond steps on some file systems
        cache.write("c", CachedBody("z".repeat(1_500), t))
        assertTrue(cache.sizeBytes() <= 3_000, "size ${cache.sizeBytes()}")
        assertNull(cache.read("a")) // the oldest file was deleted
        assertEquals("z".repeat(1_500), cache.read("c")!!.text)
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `combined status of many resources`() {
        val t1 = Instant.parse("2026-09-27T08:00:00Z")
        val t2 = Instant.parse("2026-09-27T09:00:00Z")
        val fresh = Cached("a", t2, Cached.Status.Fresh)
        val stale = Cached("b", t1, Cached.Status.Stale)
        val empty = Cached.empty<String>()
        val failed = Cached<String>(null, null, Cached.Status.Error("x", offline = true))

        assertEquals(Cached(Unit, t1, Cached.Status.Stale), Cached.combineStatus(listOf(fresh, stale, empty)))
        assertEquals(Cached.Status.Refreshing, Cached.combineStatus(listOf(fresh, stale.copy(status = Cached.Status.Refreshing))).status)
        assertIs<Cached.Status.Error>(Cached.combineStatus(listOf(fresh, failed)).status)
        assertEquals(Cached<Unit>(null, null, Cached.Status.Empty), Cached.combineStatus(listOf(empty)))
    }
}
