package app.hahn.tukplus.core.logging

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogFilesTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private fun tempDir(): File = Files.createTempDirectory("logs").toFile()
    private fun clockAt(text: String): Clock = Clock.fixed(Instant.parse(text), zone)

    @Test
    fun `session writes JSON lines into the day file`() {
        val dir = tempDir()
        val clock = clockAt("2026-09-27T08:31:48Z") // 15:31 in Bangkok
        val sink = FileLogSink(LogFileStore(dir), clock)
        val log = SessionLog(sink, clock, mapOf("app_version" to "0.1"), sessionId = "abc234")
        log.i("net", "http", "path" to "version", "status" to 200, "t" to "clash")
        log.e("app", "boom", "error" to IllegalStateException("bad"))
        sink.close()

        val lines = File(dir, "2026-09-27.jsonl").readLines()
        assertEquals(3, lines.size)
        val start = LogEvents.decode(lines[0])!!
        assertEquals("session_start", LogEvents.eventName(start))
        assertEquals("abc234", LogEvents.sessionOf(start))
        assertEquals("\"0.1\"", start["app_version"].toString())
        assertTrue(start["t"].toString().contains("+07:00"), start["t"].toString())
        val http = LogEvents.decode(lines[1])!!
        assertEquals("200", http["status"].toString())
        assertEquals("\"clash\"", http["f_t"].toString()) // A field cannot replace a reserved key.
        assertTrue(lines[2].contains("IllegalStateException"))
    }

    @Test
    fun `day file uses the local date`() {
        val dir = tempDir()
        val clock = clockAt("2026-09-27T18:00:00Z") // already 28 September in Bangkok
        val sink = FileLogSink(LogFileStore(dir), clock)
        SessionLog(sink, clock, emptyMap())
        sink.close()
        assertTrue(File(dir, "2026-09-28.jsonl").exists())
    }

    @Test
    fun `a full file continues in the next part, and a full day drops events`() {
        val dir = tempDir()
        val store = LogFileStore(dir, maxBytesPerFile = 200, maxFilesPerDay = 2)
        val day = LocalDate.of(2026, 9, 27)
        assertEquals("2026-09-27.jsonl", store.writableFile(day)!!.name)
        store.fileFor(day, 1).writeText("x".repeat(200))
        assertEquals("2026-09-27.2.jsonl", store.writableFile(day)!!.name)
        store.fileFor(day, 2).writeText("x".repeat(200))
        assertNull(store.writableFile(day))
        assertEquals(2, store.filesFor(day).size)
    }

    @Test
    fun `sink rotates when the file is full`() {
        val dir = tempDir()
        val clock = clockAt("2026-09-27T08:00:00Z")
        val store = LogFileStore(dir, maxBytesPerFile = 20_000, maxFilesPerDay = 5)
        val sink = FileLogSink(store, clock)
        val log = SessionLog(sink, clock, emptyMap())
        repeat(400) { log.i("t", "e", "n" to it, "pad" to "y".repeat(100)) }
        sink.close()
        val files = store.filesFor(LocalDate.of(2026, 9, 27))
        assertTrue(files.size >= 2, "files: ${files.map { it.name }}")
        assertEquals(401, files.sumOf { it.readLines().size })
    }

    @Test
    fun `old days are deleted`() {
        val dir = tempDir()
        val store = LogFileStore(dir, retentionDays = 14)
        listOf("2026-09-13", "2026-09-14", "2026-09-27").forEach { File(dir, "$it.jsonl").writeText("{}\n") }
        File(dir, "notes.txt").writeText("keep")
        val deleted = store.deleteOld(LocalDate.of(2026, 9, 27))
        assertEquals(listOf("2026-09-13.jsonl"), deleted.map { it.name })
        assertEquals(listOf(LocalDate.of(2026, 9, 27), LocalDate.of(2026, 9, 14)), store.days())
        assertTrue(File(dir, "notes.txt").exists())
    }

    @Test
    fun `export of days and of one session`() {
        val dir = tempDir()
        val clock = Clock.fixed(Instant.parse("2026-09-27T08:00:00Z"), ZoneOffset.ofHours(7))
        val store = LogFileStore(dir)
        val sink = FileLogSink(store, clock)
        val first = SessionLog(sink, clock, emptyMap(), sessionId = "first2")
        val second = SessionLog(sink, clock, emptyMap(), sessionId = "second")
        first.i("a", "one")
        second.i("a", "two")
        sink.close()
        File(dir, "2026-09-26.jsonl").writeText(LogEvents.encode("x", "old222", LogLevel.INFO, "a", "old", emptyMap()) + "\n")

        val exporter = LogExporter(store)
        val daysZip = File(dir, "days.zip")
        assertEquals(2, exporter.exportDays(store.days(), daysZip, "readme"))
        ZipFile(daysZip).use { zip ->
            assertEquals(setOf("README.txt", "2026-09-27.jsonl", "2026-09-26.jsonl"), zip.entries().toList().map { it.name }.toSet())
        }

        val sessionZip = File(dir, "session.zip")
        assertEquals(2, exporter.exportSession("second", store.days(), sessionZip, "readme"))
        ZipFile(sessionZip).use { zip ->
            val text = zip.getInputStream(zip.getEntry("session-second.jsonl")).bufferedReader().readText()
            assertTrue(text.contains("\"two\""))
            assertTrue(!text.contains("\"one\""))
        }
        assertNotEquals(first.sessionId, second.sessionId)
    }
}
