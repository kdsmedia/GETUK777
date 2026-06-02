package infrastructure.rabbitmq

import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory

/**
 * Opens a single long-lived [Channel] from a [RabbitMqConfig]. The channel backs both the
 * central [event.AppEventPublisher] and every [event.AppEventConsumer]; the underlying
 * connection stays open for the process lifetime.
 */
fun rabbitMqChannel(config: RabbitMqConfig): Channel {
    val factory = ConnectionFactory().apply { setUri(config.uri) }
    return factory.newConnection().createChannel()
}
