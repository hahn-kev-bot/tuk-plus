package app.hahn.tukplus.core.logging

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * Writes log lines to [LogFileStore] on one background thread, with a buffer.
 *
 * - WARN and ERROR lines are flushed to disk at once.
 * - Other lines are flushed at least every [flushIntervalMs], and when [flush] is called.
 * - The file changes at midnight (device time zone) and when a file is full.
 */
class FileLogSink(
    private val store: LogFileStore,
    private val clock: Clock,
    private val flushIntervalMs: Long = 5_000,
) {
    private val executor = Executors.newSingleThreadScheduledExecutor(ThreadFactory { runnable ->
        Thread(runnable, "tukplus-log").apply { isDaemon = true }
    })
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var currentDay: LocalDate? = null
    private var dayFullWarned: LocalDate? = null

    init {
        executor.scheduleWithFixedDelay({ flushNow() }, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS)
    }

    /** Adds a line. It is written on the log thread. */
    fun write(line: String, urgent: Boolean) {
        executor.execute {
            writeNow(line)
            if (urgent) flushNow()
        }
    }

    /** Asks the log thread to flush. Does not wait. */
    fun flush() {
        executor.execute { flushNow() }
    }

    /** Flushes and waits (at most [timeoutMs]). Use it in a crash handler. */
    fun flushBlocking(timeoutMs: Long = 2_000) {
        val future = executor.submit { flushNow() }
        runCatching { future.get(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    /** Writes a line on the calling thread and flushes. Only for a crash handler, when the log thread may not run again. */
    fun writeBlocking(line: String) {
        val future = executor.submit {
            writeNow(line)
            flushNow()
        }
        runCatching { future.get(2_000, TimeUnit.MILLISECONDS) }
    }

    fun close() {
        executor.execute {
            flushNow()
            writer?.close()
            writer = null
        }
        executor.shutdown()
        executor.awaitTermination(2, TimeUnit.SECONDS)
    }

    private fun writeNow(line: String) {
        val out = writerFor(LocalDate.now(clock)) ?: return
        out.write(line)
        out.write("\n")
        val file = currentFile
        if (file != null && file.length() + BUFFER_BYTES >= store.maxBytesPerFile) {
            // Close so that the next line opens the next part of the day.
            out.flush()
            if (file.length() >= store.maxBytesPerFile) closeWriter()
        }
    }

    private fun writerFor(day: LocalDate): BufferedWriter? {
        val open = writer
        if (open != null && day == currentDay) return open
        closeWriter()
        val file = store.writableFile(day)
        if (file == null) {
            if (dayFullWarned != day) {
                dayFullWarned = day
                System.err.println("tukplus-log: all log files for $day are full; events are dropped")
            }
            return null
        }
        currentDay = day
        currentFile = file
        return BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8), BUFFER_BYTES)
            .also { writer = it }
    }

    private fun flushNow() {
        runCatching { writer?.flush() }
    }

    private fun closeWriter() {
        runCatching { writer?.close() }
        writer = null
        currentFile = null
        currentDay = null
    }

    private companion object {
        const val BUFFER_BYTES = 16 * 1024
    }
}
