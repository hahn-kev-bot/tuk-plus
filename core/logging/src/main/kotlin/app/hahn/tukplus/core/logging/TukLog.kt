package app.hahn.tukplus.core.logging

enum class LogLevel(val code: String) { DEBUG("D"), INFO("I"), WARN("W"), ERROR("E") }

/**
 * The app's log. Each call writes one event: a [tag] (area, for example "net"), an
 * [event] name, and fields. Field values can be text, numbers, booleans, null, lists,
 * maps, JSON elements or a Throwable (written with its stack trace).
 *
 * Callers must not pass secrets. The network layer redacts its own data with [Redactor].
 */
interface TukLog {
    fun log(level: LogLevel, tag: String, event: String, fields: Map<String, Any?> = emptyMap())

    fun d(tag: String, event: String, vararg fields: Pair<String, Any?>) = log(LogLevel.DEBUG, tag, event, fields.toMap())
    fun i(tag: String, event: String, vararg fields: Pair<String, Any?>) = log(LogLevel.INFO, tag, event, fields.toMap())
    fun w(tag: String, event: String, vararg fields: Pair<String, Any?>) = log(LogLevel.WARN, tag, event, fields.toMap())
    fun e(tag: String, event: String, vararg fields: Pair<String, Any?>) = log(LogLevel.ERROR, tag, event, fields.toMap())

    /** Writes all buffered events to disk now. */
    fun flush() {}

    companion object {
        /** A log that drops all events. For tests and tools. */
        val NONE: TukLog = object : TukLog {
            override fun log(level: LogLevel, tag: String, event: String, fields: Map<String, Any?>) = Unit
        }
    }
}
