package api.webhook

import infrastructure.aggregator.gamingflow.webhook.GamingFlowWebhook
import infrastructure.aggregator.onegamehub.webhook.OneGameHubWebhook
import infrastructure.aggregator.pragmatic.webhook.PragmaticWebhook
import infrastructure.aggregator.tech01sport.webhook.Tech01SportWebhook
import infrastructure.aggregator.tongame.webhook.TongameWebhook
import io.ktor.server.application.Application
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get

/**
 * Inbound aggregator webhooks — a separate surface from our own game.v1 API (api/grpc/GrpcModule.kt).
 * Each aggregator brings its own webhook under `/api/webhook`. Add a new one by wiring its routes here.
 */
fun Application.configureWebhook() {
    val oneGameHubWebhook = get<OneGameHubWebhook>()
    val pragmaticWebhook = get<PragmaticWebhook>()
    val tongameWebhook = get<TongameWebhook>()
    val gamingFlowWebhook = get<GamingFlowWebhook>()
    val tech01SportWebhook = get<Tech01SportWebhook>()

    routing {
        route("/api/webhook") {
            with(oneGameHubWebhook) { route() }
            with(pragmaticWebhook) { route() }
            with(tongameWebhook) { route() }
            with(gamingFlowWebhook) { route() }
            with(tech01SportWebhook) { route() }
        }
    }
}
