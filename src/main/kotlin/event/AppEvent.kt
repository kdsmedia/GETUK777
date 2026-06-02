package event

import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DeliverCallback
import domain.exception.system.EventPublishingException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

/** Shared JSON codec for the event envelope and every snapshot payload. */
val appJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Single shared topic exchange every engine publishes to / consumes from. */
val EVENT_EXCHANGE: String = System.getenv("EVENT_EXCHANGE") ?: "crm.exchange"

/** Declares the shared topic exchange once at startup. */
fun declareEventExchange(channel: Channel) {
    channel.exchangeDeclare(EVENT_EXCHANGE, BuiltinExchangeType.TOPIC, true)
}

/**
 * Uniform event contract. Every event carries exactly one [data] snapshot; the correlation
 * key [playerId] is derived from [data]. The companion [Meta] supplies the route, the payload
 * serializer, and a rebuild factory — the only place that knows how to wire the event.
 */
interface AppEvent<T : Any> {
    val playerId: String

    val data: T

    interface Meta<T : Any> {
        val route: String

        val serializer: KSerializer<T>

        fun create(data: T): AppEvent<T>
    }
}

/**
 * The one place that owns serialization + channel access. Wraps any [AppEvent] in the uniform
 * `{ "playerId": ..., "data": ... }` envelope and publishes it on the event's route.
 *
 * Open so non-publishing entrypoints (e.g. the sync CLI) can supply a no-op override.
 */
open class AppEventPublisher(private val channel: Channel?) {

    open fun publish(event: AppEvent<*>) {
        val channel = channel ?: throw EventPublishingException("AppEventPublisher has no channel")
        @Suppress("UNCHECKED_CAST")
        val meta = event::class.companionObjectInstance as AppEvent.Meta<Any>
        val envelope = buildJsonObject {
            put("playerId", JsonPrimitive(event.playerId))
            put("data", appJson.encodeToJsonElement(meta.serializer, event.data))
        }
        channel.basicPublish(
            EVENT_EXCHANGE,
            meta.route,
            null,
            appJson.encodeToString(JsonObject.serializer(), envelope).toByteArray(),
        )
    }
}

/** No-op publisher for entrypoints that must never emit events (e.g. the aggregator sync CLI). */
object NoOpAppEventPublisher : AppEventPublisher(null) {
    override fun publish(event: AppEvent<*>) = Unit
}

/**
 * Generic consumer base. The queue name is the consumer's simple class name; it auto-binds to
 * the shared exchange on the event's route, decodes the envelope, rebuilds the event, and hands
 * it to [handle]. Subclasses are read-only routers — they own no codec or channel logic.
 *
 * Mirrors the crm reference: the delivery callback decodes the envelope and runs [handle] via
 * `runBlocking`. Auto-ack stays on — these consumers feed at-most-once Redis projections, so a
 * failed handler is logged by the client and not requeued.
 */
abstract class AppEventConsumer<E : AppEvent<*>>(channel: Channel, type: KClass<E>) {

    init {
        @Suppress("UNCHECKED_CAST")
        val meta = type.companionObjectInstance as AppEvent.Meta<Any>
        val queue = this::class.simpleName!!
        channel.queueDeclare(queue, true, false, false, null)
        channel.queueBind(queue, EVENT_EXCHANGE, meta.route)
        val callback = DeliverCallback { _, delivery ->
            val envelope = appJson.parseToJsonElement(delivery.body.decodeToString()).jsonObject
            val data = appJson.decodeFromJsonElement(meta.serializer, envelope.getValue("data"))
            @Suppress("UNCHECKED_CAST")
            val event = meta.create(data) as E
            runBlocking { handle(event) }
        }
        channel.basicConsume(queue, true, callback, { _ -> })
    }

    abstract suspend fun handle(event: E)
}
