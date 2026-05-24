package api.webhook

import infrastructure.aggregator.onegamehub.webhook.OneGameHubWebhook
import infrastructure.aggregator.pragmatic.webhook.PragmaticWebhook
import infrastructure.aggregator.tongame.webhook.TongameWalletGrpcService
import io.grpc.ServerBuilder
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.ktor.ext.get
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.nekgamebling.Webhook")

/**
 * Inbound aggregator webhooks — a separate surface from our own game.v1 API (api/grpc/GrpcModule.kt).
 * Each aggregator brings its own webhook in whatever transport it uses: REST (OneGameHub, Pragmatic),
 * gRPC (TONGame), sockets, etc. Add a new one by wiring it into the matching transport below.
 */
fun Application.configureWebhook() {
    configureRestWebhook()
    configureGrpcWebhook()
}

private fun Application.configureRestWebhook() {
    val oneGameHubWebhook = get<OneGameHubWebhook>()
    val pragmaticWebhook = get<PragmaticWebhook>()

    routing {
        route("/api/webhook") {
            with(oneGameHubWebhook) { route() }
            with(pragmaticWebhook) { route() }
        }
    }
}

private fun Application.configureGrpcWebhook() {
    val port = webhookGrpcPort()

    launch(Dispatchers.IO) {
        val server = ServerBuilder.forPort(port)
            .addService(get<TongameWalletGrpcService>())
            .build()
            .start()

        logger.info("Webhook gRPC server started on port $port")

        Runtime.getRuntime().addShutdownHook(Thread {
            server.shutdown()
        })

        server.awaitTermination()
    }
}

private fun webhookGrpcPort(): Int = System.getenv("WEBHOOK_GRPC_PORT")?.toIntOrNull() ?: 5051
