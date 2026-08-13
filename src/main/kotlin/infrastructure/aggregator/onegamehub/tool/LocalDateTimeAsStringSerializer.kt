package infrastructure.aggregator.onegamehub.tool

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.char
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * OneGameHub accepts one date shape and rejects everything else with
 * `` `start_at` should be a datetime string matched with template "%Y-%m-%d %H:%M:%S" ``.
 * That is ISO with a space instead of the `T`, which is exactly what `LocalDateTime.toString()`
 * does NOT produce — so the format is spelled out here rather than inherited.
 */
object LocalDateTimeAsStringSerializer : KSerializer<LocalDateTime> {

    private val FORMAT = LocalDateTime.Format {
        year(); char('-'); monthNumber(); char('-'); dayOfMonth()
        char(' ')
        hour(); char(':'); minute(); char(':'); second()
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LocalDateTime) {
        encoder.encodeString(value.format(FORMAT))
    }

    override fun deserialize(decoder: Decoder): LocalDateTime =
        FORMAT.parse(decoder.decodeString())
}
