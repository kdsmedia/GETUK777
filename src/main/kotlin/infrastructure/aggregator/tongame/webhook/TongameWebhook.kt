package infrastructure.aggregator.tongame.webhook

import application.Bus
import application.command.session.EndRoundSessionCommand
import application.command.session.PlaceSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.query.session.FindSessionBalanceQuery
import application.query.session.FindSessionQuery
import domain.exception.conflict.ConflictException
import domain.exception.forbidden.ForbiddenException
import domain.exception.notfound.NotFoundException
import domain.exception.notfound.SessionNotFoundException
import domain.model.PlayerBalance
import domain.model.Session
import domain.vo.Amount
import domain.vo.Currency
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * Inbound TONGame wallet webhooks (provider → operator). Four flat POST paths under
 * `/api/webhook/tongame`. Sessions resolve by our own session token via [FindSessionQuery].
 *
 * TONGame differs from the other aggregators: currency is NOT locked to the session — the
 * player can switch currency in-game, so every wallet call carries its own `currency`. We
 * override the resolved session's currency with the request's before driving the pipeline,
 * so the wallet operation hits the right currency. Money is string-encoded nano-TON.
 */
class TongameWebhook(private val bus: Bus) {

    fun Route.route() = route("/tongame") {
        post("/balance") {
            val body = call.receive<BalanceRequest>()
            call.respondBalance {
                val session = resolveSession(body.sessionToken, body.currency)
                bus(FindSessionBalanceQuery(session))
            }
        }

        post("/place") {
            val body = call.receive<RoundAmountRequest>()
            call.respondBalance {
                val session = resolveSession(body.sessionToken, body.currency)
                bus(
                    PlaceSpinSessionCommand(
                        session = session,
                        externalRoundId = body.roundId,
                        externalSpinId = "${body.roundId}:place",
                        amount = Amount(body.amount.toLong()),
                    )
                )
            }
        }

        post("/settle") {
            val body = call.receive<RoundAmountRequest>()
            call.respondBalance {
                val session = resolveSession(body.sessionToken, body.currency)
                bus(
                    SettleSpinSessionCommand(
                        session = session,
                        externalRoundId = body.roundId,
                        externalSpinId = "${body.roundId}:settle",
                        amount = Amount(body.amount.toLong()),
                    )
                )
            }
        }

        post("/closeRound") {
            val body = call.receive<CloseRoundRequest>()
            call.respondBalance {
                val session = resolveSession(body.sessionToken, body.currency)
                bus(EndRoundSessionCommand(session = session, externalRoundId = body.roundId))
                bus(FindSessionBalanceQuery(session))
            }
        }
    }

    /** Resolve our session by token and pin the request's currency onto it for this operation. */
    private suspend fun resolveSession(token: String, currency: String): Session =
        bus(FindSessionQuery(token)).copy(currency = Currency(currency))

    private suspend fun ApplicationCall.respondBalance(block: suspend () -> PlayerBalance) {
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

        respond(BalanceResponse(amount = balance.total.value.toString(), currency = balance.currency.value))
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
