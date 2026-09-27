package app.hahn.tukplus.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * The Tuk API sends the same field as a string in one record and as a number in
 * another (for example "price": "380" and "price": 0). These serializers accept both.
 */

private fun JsonDecoder.primitiveOrNull(): JsonPrimitive? =
    when (val element: JsonElement = decodeJsonElement()) {
        is JsonNull -> null
        is JsonPrimitive -> element
        else -> null // Objects and arrays are not valid here. Treat them as missing.
    }

/** Accepts a string, number or boolean, and gives its text. */
object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        return json.primitiveOrNull()?.content
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** Accepts a number or a numeric string. Other values become null. */
object LenientDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive = json.primitiveOrNull() ?: return null
        return primitive.doubleOrNull ?: primitive.content.trim().toDoubleOrNull()
    }

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

/** Accepts a whole number or a numeric string. Decimals are cut, like `parseInt` in the web app. */
object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeInt()
        val primitive = json.primitiveOrNull() ?: return null
        primitive.longOrNull?.let { return it.toInt() }
        return parseIntLikeJavaScript(primitive.content)
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/** Accepts a boolean, or the strings "true"/"false". Other values become null. */
object LenientBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return json.primitiveOrNull()?.content?.lowercase()?.toBooleanStrictOrNull()
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}

/**
 * The same result as JavaScript `parseInt(text, 10)`: read an optional sign and the
 * leading digits, ignore the rest. Gives null when there are no leading digits.
 */
fun parseIntLikeJavaScript(text: String): Int? {
    val trimmed = text.trimStart()
    var index = 0
    val negative = trimmed.startsWith("-")
    if (negative || trimmed.startsWith("+")) index = 1
    val start = index
    while (index < trimmed.length && trimmed[index].isDigit()) index++
    if (index == start) return null
    val value = trimmed.substring(start, index).toLongOrNull() ?: return null
    return (if (negative) -value else value).toInt()
}
