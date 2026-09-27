package app.hahn.tukplus.core.pricing

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/**
 * JavaScript value rules that change amounts (docs/pricing.md §17).
 *
 * The web app keeps prices as text or numbers and uses JavaScript coercion. All amounts
 * here are [Double], like JavaScript numbers, so float errors and NaN behave the same.
 * A Kotlin `null` is JavaScript `undefined` (a missing key), [JsonNull] is JavaScript `null`.
 */
internal object Js {
    /** JavaScript `!!value`. */
    fun truthy(value: JsonElement?): Boolean = when (value) {
        null, JsonNull -> false
        is JsonPrimitive -> when {
            value.isString -> value.content.isNotEmpty()
            value.booleanOrNull != null -> value.booleanOrNull!!
            else -> truthy(value.content.toDoubleOrNull() ?: Double.NaN)
        }
        is JsonObject, is JsonArray -> true
    }

    fun truthy(value: Double?): Boolean = value != null && value != 0.0 && !value.isNaN()

    /** JavaScript `Number(value)` (also what `value / 100` and `a - value` do). */
    fun number(value: JsonElement?): Double = when (value) {
        null -> Double.NaN
        JsonNull -> 0.0
        is JsonPrimitive -> when {
            value.isString -> number(value.content)
            value.booleanOrNull != null -> if (value.booleanOrNull!!) 1.0 else 0.0
            else -> value.content.toDoubleOrNull() ?: Double.NaN
        }
        is JsonArray -> if (value.isEmpty()) 0.0 else if (value.size == 1) number(value[0]) else Double.NaN
        is JsonObject -> Double.NaN
    }

    private val DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")

    /** JavaScript `Number(text)`. */
    fun number(text: String): Double {
        val t = text.trim()
        if (t.isEmpty()) return 0.0
        return when {
            t == "Infinity" || t == "+Infinity" -> Double.POSITIVE_INFINITY
            t == "-Infinity" -> Double.NEGATIVE_INFINITY
            t.startsWith("0x") || t.startsWith("0X") -> t.substring(2).toLongOrNull(16)?.toDouble() ?: Double.NaN
            DECIMAL.matches(t) -> t.toDouble()
            else -> Double.NaN
        }
    }

    /** JavaScript `parseInt(value)` (base 10). NaN when there are no leading digits. */
    fun parseInt(value: JsonElement?): Double = when (value) {
        null, JsonNull -> Double.NaN
        is JsonPrimitive -> when {
            value.isString -> parseInt(value.content)
            value.booleanOrNull != null -> Double.NaN
            else -> parseInt(value.content.toDoubleOrNull() ?: Double.NaN)
        }
        else -> Double.NaN
    }

    /** JavaScript `parseInt(text)`: optional sign and leading digits after leading white space. */
    fun parseInt(text: String): Double {
        val t = text.trimStart()
        var i = 0
        val negative = t.startsWith("-")
        if (negative || t.startsWith("+")) i = 1
        val start = i
        while (i < t.length && t[i] in '0'..'9') i++
        if (i == start) return Double.NaN
        val digits = t.substring(start, i).toBigDecimal().toDouble()
        return if (negative) -digits else digits
    }

    /** JavaScript `parseInt(number)`: the number is made into text first. */
    fun parseInt(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return Double.NaN
        val a = abs(value)
        if (a == 0.0) return 0.0
        // JavaScript writes 1e21 and more, and less than 1e-6, with an exponent: parseInt then reads one digit.
        if (a >= 1e21 || a < 1e-6) {
            val first = BigDecimal(a).round(java.math.MathContext(1, RoundingMode.DOWN)).unscaledValue().toInt()
            return if (value < 0) -first.toDouble() else first.toDouble()
        }
        val t = if (value < 0) -floor(a) else floor(a)
        return if (t == 0.0) 0.0 else t
    }

    /** JavaScript `Math.round`: the nearest integer, a half goes up (to +∞). */
    fun round(x: Double): Double {
        if (x.isNaN() || x.isInfinite()) return x
        if (abs(x) >= 4.503599627370496E15) return x
        return Math.round(x).toDouble()
    }

    /** JavaScript `Number.prototype.toFixed(2)` for |x| < 1e21. */
    fun toFixed2(x: Double): String {
        if (x.isNaN()) return "NaN"
        if (x.isInfinite()) return if (x > 0) "Infinity" else "-Infinity"
        val negative = x < 0
        val text = BigDecimal(abs(x)).setScale(2, RoundingMode.HALF_UP).toPlainString()
        return if (negative && text != "0.00") "-$text" else text
    }

    /** JavaScript `a >= b` where `b` can be `null` (then 0). */
    fun gte(a: Double, b: Double?): Boolean = a >= (b ?: 0.0)

    /** A number for JSON: whole numbers without ".0", NaN as null (like `JSON.stringify`). */
    fun json(x: Double?): JsonElement = when {
        x == null || x.isNaN() || x.isInfinite() -> JsonNull
        x == floor(x) && abs(x) < 9.0E15 -> JsonPrimitive(x.toLong())
        else -> JsonPrimitive(x)
    }
}

/** An amount in baht for the screens: null when the web code gives NaN. Fractions are cut. */
internal fun Double.money(): Int? = if (isNaN() || isInfinite()) null else toLong().toInt()

internal fun Double?.moneyOrNull(): Int? = this?.money()
