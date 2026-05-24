package infrastructure.aggregator.tongame.webhook

import application.Bus
import application.command.session.EndRoundSessionCommand
import application.command.session.PlaceSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.query.session.FindSessionBalanceQuery
import domain.exception.conflict.ConflictException
import domain.exception.forbidden.ForbiddenException
import domain.exception.notfound.NotFoundException
import domain.exception.notfound.SessionNotFoundException
import domain.model.PlayerBalance
import domain.vo.Amount
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * Inbound TONGame wallet webhooks (provider → operator, slot v1). Four flat POST paths under
 * the registered `webhookUrl`. Sessions resolve by our own session token via the spin pipeline
 * ([Bus]). Money is string-encoded nano-TON on the wire.
 */
class TongameWebhook(private val bus: Bus) {

    fun Route.route() = route("/tongame") {
        post("/balance") {
            val body = call.receive<BalanceRequest>()
            call.respondBalance(body.currency) {
                bus(FindSessionBalanceQuery(body.sessionToken))
            }
        }

        post("/place") {
            val body = call.receive<RoundAmountRequest>()
            call.respondBalance(body.currency) {
                bus(
                    PlaceSpinSessionCommand(
                        sessionToken = body.sessionToken,
                        externalRoundId = body.roundId,
                        externalSpinId = "${body.roundId}:place",
                        amount = Amount(body.amount.toLong()),
                    )
                )
            }
        }

        post("/settle") {
            val body = call.receive<RoundAmountRequest>()
            call.respondBalance(body.currency) {
                bus(
                    SettleSpinSessionCommand(
                        sessionToken = body.sessionToken,
                        externalRoundId = body.roundId,
                        externalSpinId = "${body.roundId}:settle",
                        amount = Amount(body.amount.toLong()),
                    )
                )
            }
        }

        post("/closeRound") {
            val body = call.receive<CloseRoundRequest>()
            call.respondBalance(body.currency) {
                bus(EndRoundSessionCommand(sessionToken = body.sessionToken, externalRoundId = body.roundId))
                bus(FindSessionBalanceQuery(body.sessionToken))
            }
        }
    }

    private suspend fun ApplicationCall.respondBalance(currency: String, block: suspend () -> PlayerBalance) {
        val balance = try {
            block()
        } catch (e: SessionNotFoundException) {
            respond(HttpStatusCode.Unauthorized, ErrorResponse("UNKNOWN_SESSION", e.message))
            return
        } catch (e: ForbiddenException) {
            respond(HttpStatusCode.Conflict, ErrorResponse("REJECTED", e.message))
            return
        } catch (e: NotFoundException) {
            respond(HttpStatusCode.Conflict, ErrorResponse("REJECTED", e.message))
            return
        } catch (e: ConflictException) {
            respond(HttpStatusCode.Conflict, ErrorResponse("REJECTED", e.message))
            return
        }

        respond(BalanceResponse(amount = balance.total.value.toString(), currency = currency))
    }

    @Serializable
    private data class BalanceRequest(val sessionToken: String, val currency: String)

    @Serializable
    private data class RoundAmountRequest(
        val sessionToken: String,
        val amount: String,
        val currency: String,
        val roundId: String,
    )

    @Serializable
    private data class CloseRoundRequest(
        val sessionToken: String,
        val roundId: String,
        val currency: String,
    )

    @Serializable
    private data class BalanceResponse(val amount: String, val currency: String)

    @Serializable
    private data class ErrorResponse(val code: String, val message: String?)
}
