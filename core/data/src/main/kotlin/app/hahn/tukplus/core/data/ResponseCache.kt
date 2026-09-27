package app.hahn.tukplus.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/** A cached response body. */
data class CachedBody(val text: String, val fetchedAt: Instant)

/** Stores raw response bodies by cache key. */
interface ResponseCache {
    suspend fun read(key: String): CachedBody?
    suspend fun write(key: String, body: CachedBody)
    suspend fun clear()
}

/**
 * Stores each response in its own file. The first line is a small JSON header
 * (key and time); the rest is the body.
 *
 * - Writes are atomic: the file is written under a temporary name, then renamed.
 * - When the folder is larger than [maxBytes], the oldest files are deleted.
 *
 * Files are used instead of SQLite rows, because one response (the shop list) is
 * already 600 KB, and Android cannot read SQLite rows larger than about 2 MB.
 */
class FileResponseCache(
    private val dir: File,
    private val maxBytes: Long = 64L * 1024 * 1024,
) : ResponseCache {

    @Serializable
    private data class Header(val key: String, val fetchedAt: Long)

    private val json = Json { ignoreUnknownKeys = true }

    init {
        dir.mkdirs()
    }

    override suspend fun read(key: String): CachedBody? = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        if (!file.exists()) return@withContext null
        runCatching {
            val text = file.readText()
            val newline = text.indexOf('\n')
            val header = json.decodeFromString(Header.serializer(), text.substring(0, newline))
            if (header.key != key) return@runCatching null // Hash collision: treat as not cached.
            CachedBody(text.substring(newline + 1), Instant.ofEpochMilli(header.fetchedAt))
        }.getOrNull()
    }

    override suspend fun write(key: String, body: CachedBody) = withContext(Dispatchers.IO) {
        val file = fileFor(key)
        val temp = File(dir, file.name + ".tmp")
        val header = json.encodeToString(Header.serializer(), Header(key, body.fetchedAt.toEpochMilli()))
        temp.writeText(header + "\n" + body.text)
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
        trim()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    /** Total size of the cache files. */
    fun sizeBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    private fun trim() {
        val files = dir.listFiles().orEmpty().filter { it.name.endsWith(".json") }
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            total -= file.length()
            file.delete()
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}

/** A cache in memory only. For tests. */
class MemoryResponseCache : ResponseCache {
    private val map = java.util.concurrent.ConcurrentHashMap<String, CachedBody>()
    override suspend fun read(key: String): CachedBody? = map[key]
    override suspend fun write(key: String, body: CachedBody) {
        map[key] = body
    }
    override suspend fun clear() = map.clear()
}
