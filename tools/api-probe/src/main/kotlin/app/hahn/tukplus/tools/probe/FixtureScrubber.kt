package app.hahn.tukplus.tools.probe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Removes personal data from recorded responses before they go into the repository:
 * contact fields, block lists and notification ids are removed; emails and phone
 * numbers in any text are replaced. The shape of the data stays the same where
 * our models read it.
 */
object FixtureScrubber {
    private val json = Json { prettyPrint = false }

    /** Keys that are removed. None of them is read by the app. */
    val REMOVED_KEYS = setOf(
        "email", "billing_email", "notifications_email", "boss_phone_number",
        "customer_blacklist", "driver_blacklist", "notifications", "channels",
        "telegram_invite_link", "prompt_pay", "paynow", "created_by", "review_sites", "quotes",
    )
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?<![\\w-])(\\+?66[\\s-]?\\d[\\d\\s-]{7,11}\\d|0\\d{1,2}[\\s-]?\\d{3}[\\s-]?\\d{3,4})(?![\\w-])")

    fun scrubBody(body: String): String {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return scrubText(body)
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return scrubText(body)
        return json.encodeToString(JsonElement.serializer(), scrub(element))
    }

    fun scrub(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it !in REMOVED_KEYS }.mapValues { (key, value) ->
                if (key == "phone_number" && value is JsonPrimitive && value.isString) JsonPrimitive(FAKE_PHONE) else scrub(value)
            },
        )
        is JsonArray -> JsonArray(element.map { scrub(it) })
        is JsonPrimitive -> if (element.isString) JsonPrimitive(scrubText(element.content)) else element
    }

    fun scrubText(text: String): String = text.replace(EMAIL, "someone@example.com").replace(PHONE, FAKE_PHONE)

    const val FAKE_PHONE = "+66800000000"
}
