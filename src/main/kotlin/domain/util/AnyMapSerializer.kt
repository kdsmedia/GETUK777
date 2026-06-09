package domain.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Serializes an untyped `Map<String, Any>` to/from JSON. kotlinx has no serializer for `Any`, so
 * values are dispatched by runtime type to JSON primitives on the way out and unwrapped on the
 * way back. Only used inside the JSON event format.
 */
object AnyMapSerializer : KSerializer<Map<String, Any>> {

    override val descriptor: SerialDescriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Any>) {
        val json = encoder as JsonEncoder
        json.encodeJsonElement(buildJsonObject {
            value.forEach { (key, raw) -> put(key, raw.toJsonElement()) }
        })
    }

    override fun deserialize(decoder: Decoder): Map<String, Any> {
        val json = decoder as JsonDecoder
        return json.decodeJsonElement().jsonObject.toAnyMap()
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Map<*, *> -> buildJsonObject { forEach { (k, v) -> put(k.toString(), v.toJsonElement()) } }
        is Iterable<*> -> buildJsonArray { forEach { add(it.toJsonElement()) } }
        else -> JsonPrimitive(toString())
    }

    private fun JsonObject.toAnyMap(): Map<String, Any> =
        entries.mapNotNull { (key, element) -> element.toAnyOrNull()?.let { key to it } }.toMap()

    private fun JsonElement.toAnyOrNull(): Any? = when (this) {
        is JsonNull -> null
        is JsonObject -> toAnyMap()
        is JsonArray -> mapNotNull { it.toAnyOrNull() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
    }
}
