package app.hahn.tukplus.core.logging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * One log line (JSON Lines format):
 * `{"t":"2026-09-27T15:31:48.017+07:00","s":"k3x9qa","lvl":"I","tag":"net","ev":"http",…fields}`
 */
object LogEvents {
    const val TIME = "t"
    const val SESSION = "s"
    const val LEVEL = "lvl"
    const val TAG = "tag"
    const val EVENT = "ev"
    private val reserved = setOf(TIME, SESSION, LEVEL, TAG, EVENT)

    val json = Json { encodeDefaults = true }

    fun encode(
        time: String,
        session: String,
        level: LogLevel,
        tag: String,
        event: String,
        fields: Map<String, Any?>,
    ): String {
        val content = LinkedHashMap<String, JsonElement>()
        content[TIME] = JsonPrimitive(time)
        content[SESSION] = JsonPrimitive(session)
        content[LEVEL] = JsonPrimitive(level.code)
        content[TAG] = JsonPrimitive(tag)
        content[EVENT] = JsonPrimitive(event)
        for ((key, value) in fields) {
            // A field must not replace a reserved key. Keep it under another name.
            val name = if (key in reserved) "f_$key" else key
            content[name] = toJson(value)
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(content))
    }

    fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Enum<*> -> JsonPrimitive(value.name)
        is Throwable -> JsonPrimitive(value.stackTraceToString())
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map { toJson(it) })
        is Array<*> -> JsonArray(value.map { toJson(it) })
        else -> JsonPrimitive(value.toString())
    }

    /** Reads a line back. Gives null for a line that is not a JSON object (for example a cut last line). */
    fun decode(line: String): JsonObject? = try {
        json.parseToJsonElement(line) as? JsonObject
    } catch (_: Exception) {
        null
    }

    fun sessionOf(event: JsonObject): String? = (event[SESSION] as? JsonPrimitive)?.contentOrNull

    fun eventName(event: JsonObject): String? = event[EVENT]?.jsonPrimitive?.contentOrNull
}
