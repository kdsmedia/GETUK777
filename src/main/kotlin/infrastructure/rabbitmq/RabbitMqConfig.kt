package infrastructure.rabbitmq

import java.net.URLEncoder

data class RabbitMqConfig(
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val tls: Boolean,
) {
    val uri: String get() {
        val scheme = if (tls) "amqps" else "amqp"
        // Credentials must be percent-encoded: an unescaped '@' in the password
        // (Amazon MQ generates them) shifts the parsed host and the connection
        // lands on localhost.
        val user = URLEncoder.encode(user, Charsets.UTF_8)
        val password = URLEncoder.encode(password, Charsets.UTF_8)
        return "$scheme://$user:$password@$host:$port"
    }
}
