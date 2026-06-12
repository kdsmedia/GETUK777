package infrastructure.rabbitmq

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory

/**
 * Opens the single long-lived [Connection] from a [RabbitMqConfig]; it stays open for the
 * process lifetime. The publisher and the consumers each run on their OWN channel from this
 * connection — publisher confirms and consumer deliveries must never share a channel, so a
 * channel-level error on one side cannot take down the other.
 */
fun rabbitMqConnection(config: RabbitMqConfig): Connection {
    val factory = ConnectionFactory().apply { setUri(config.uri) }
    return factory.newConnection()
}
