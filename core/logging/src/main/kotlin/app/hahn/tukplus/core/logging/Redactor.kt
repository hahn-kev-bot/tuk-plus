package app.hahn.tukplus.core.logging

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest

/**
 * Removes secrets and personal data from log values (PLAN.md §9.3).
 *
 * - User ids, device uuids and similar ids become a short hash. The same value always
 *   gives the same hash on one install, so one user can still be followed in the logs.
 * - Phone numbers and email addresses are masked.
 * - Bank account numbers keep only the last 4 digits.
 * - Tokens are removed.
 *
 * @param salt a random value per install. It makes the hashes useless outside this install.
 */
class Redactor(private val salt: String) {

    fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((salt + value).toByteArray())
        return "#" + digest.take(5).joinToString("") { "%02x".format(it) }
    }

    /** "+66812345678" → "+66 8x xxx 5678". */
    fun maskPhone(value: String): String {
        val digits = value.filter { it.isDigit() }
        if (digits.length < 6) return "***"
        val last4 = digits.takeLast(4)
        return if (value.trim().startsWith("+") && digits.length > 9) {
            val country = digits.take(digits.length - 9)
            "+$country ${digits[country.length]}x xxx $last4"
        } else {
            "xxx $last4"
        }
    }

    /** "somchai@example.com" → "s***@example.com". */
    fun maskEmail(value: String): String {
        val at = value.indexOf('@')
        if (at <= 0) return "***"
        return value.first() + "***" + value.substring(at)
    }

    fun maskAccount(value: String): String {
        val digits = value.filter { it.isDigit() }
        return if (digits.length <= 4) "****" else "****" + digits.takeLast(4)
    }

    /** Redacts ids and personal values in a URL path and query. */
    fun redactUrl(pathAndQuery: String): String {
        val path = pathAndQuery.substringBefore('?')
        val query = pathAndQuery.substringAfter('?', missingDelimiterValue = "")
        val segments = path.split('/')
        val redactedPath = segments.mapIndexed { index, segment ->
            val previous = segments.getOrNull(index - 1)
            if (previous != null && previous in USER_PATH_PREFIXES && segment.isNotEmpty()) hash(segment) else segment
        }.joinToString("/")
        if (query.isEmpty()) return redactedPath
        val redactedQuery = query.split('&').joinToString("&") { part ->
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', missingDelimiterValue = "")
            when {
                value.isEmpty() -> part
                key in ID_KEYS -> "$key=${hash(value)}"
                key in PHONE_KEYS -> "$key=${maskPhone(value)}"
                key in EMAIL_KEYS -> "$key=${maskEmail(value)}"
                key in DROP_KEYS -> "$key=[removed]"
                else -> part
            }
        }
        return "$redactedPath?$redactedQuery"
    }

    /** Redacts a JSON value by key names. The `id` of an object under a "user" key is also hashed. */
    fun redactJson(element: JsonElement, parentKey: String? = null): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.mapValues { (key, value) -> redactField(key, value, parentKey) })
        is JsonArray -> JsonArray(element.map { redactJson(it, parentKey) })
        else -> element
    }

    private fun redactField(key: String, value: JsonElement, parentKey: String?): JsonElement {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString || it.contentOrNull?.firstOrNull()?.isDigit() == true }?.contentOrNull
        return when {
            value is JsonNull -> value
            key in DROP_KEYS -> JsonPrimitive("[removed]")
            text != null && text.isEmpty() -> value
            text != null && (key in ID_KEYS || (key == "id" && parentKey in USER_OBJECT_KEYS)) -> JsonPrimitive(hash(text))
            text != null && key in PHONE_KEYS -> JsonPrimitive(maskPhone(text))
            text != null && key in EMAIL_KEYS -> JsonPrimitive(maskEmail(text))
            text != null && key in ACCOUNT_KEYS -> JsonPrimitive(maskAccount(text))
            value is JsonObject || value is JsonArray -> redactJson(value, key)
            else -> value
        }
    }

    /** Redacts a request or response body. Bodies that are not JSON are kept only if short. */
    fun redactBody(body: String, maxChars: Int = 4096): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            val parsed = runCatching { LogEvents.json.parseToJsonElement(trimmed) }.getOrNull()
            if (parsed != null) return LogEvents.json.encodeToString(JsonElement.serializer(), redactJson(parsed)).take(maxChars)
        }
        return trimmed.take(maxChars)
    }

    companion object {
        /** Keys that hold ids of people or devices. */
        val ID_KEYS = setOf("user_id", "created_by", "uuid", "handled_by", "worker_id", "driver_id", "userID", "user")
        val USER_OBJECT_KEYS = setOf("user", "driver", "customer")
        val PHONE_KEYS = setOf(
            "phone_number", "source_user_id", "mobile", "recipient_phone", "local_contact", "phone",
        )
        val EMAIL_KEYS = setOf("email")
        val ACCOUNT_KEYS = setOf("account_number")
        val DROP_KEYS = setOf("notification_token", "token", "authorization", "Authorization", "code", "client_secret")
        /** Path segments that are followed by a user or device id. */
        val USER_PATH_PREFIXES = setOf(
            "users", "user_addresses", "devices", "logout", "line_users", "fb_users", "sms_users", "facebook_users",
        )
    }
}
