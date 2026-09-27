package app.hahn.tukplus.core.logging

import java.security.SecureRandom
import java.time.Clock
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The log of one app session. A session starts when the app process starts.
 * All lines carry the session id, so one day file can hold many sessions.
 *
 * The first line of a session is the `session_start` event with [startInfo].
 */
class SessionLog(
    private val sink: FileLogSink,
    private val clock: Clock,
    startInfo: Map<String, Any?>,
    val sessionId: String = newSessionId(),
    private val minLevel: LogLevel = LogLevel.DEBUG,
    /** Also print events here, for example to Logcat in debug builds. */
    private val echo: ((LogLevel, String) -> Unit)? = null,
) : TukLog {

    init {
        log(LogLevel.INFO, "app", "session_start", startInfo)
    }

    override fun log(level: LogLevel, tag: String, event: String, fields: Map<String, Any?>) {
        if (level.ordinal < minLevel.ordinal) return
        val line = LogEvents.encode(now(), sessionId, level, tag, event, fields)
        sink.write(line, urgent = level.ordinal >= LogLevel.WARN.ordinal)
        echo?.invoke(level, line)
    }

    override fun flush() = sink.flush()

    /** Writes a crash and waits until it is on disk. */
    fun logCrashBlocking(thread: String, throwable: Throwable) {
        val line = LogEvents.encode(
            now(), sessionId, LogLevel.ERROR, "app", "crash",
            mapOf("thread" to thread, "exception" to throwable::class.java.name, "stack" to throwable),
        )
        sink.writeBlocking(line)
    }

    private fun now(): String = OffsetDateTime.now(clock).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    companion object {
        private val random = SecureRandom()
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

        fun newSessionId(): String = (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }
}
