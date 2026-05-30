package infrastructure.aggregator.tongame.webhook

import application.Bus
import application.command.session.EndRoundSessionCommand
import application.command.session.PlaceSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.port.external.IPlayerPort
import application.query.session.FindSessionBalanceQuery
import application.query.session.FindSessionByPlayerIdQuery
import domain.exception.conflict.ConflictException
import domain.exception.forbidden.ForbiddenException
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.forbidden.MaxPlaceSpinException
import domain.exception.notfound.NotFoundException
import domain.exception.notfound.SessionNotFoundException
import domain.model.Session
import domain.vo.Amount
import domain.vo.Currency
import infrastructure.aggregator.tongame.TongameConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * Inbound TONGame webhooks (provider → operator). Six flat POST paths under
 * `/api/webhook/tongame`: `/player`, `/balance`, `/round/open`, `/round/close`, `/debit`,
 * `/credit`.
 *
 * Identity: the provider echoes back, as `playerId`, the value we passed to `POST /api/v1/session`
 * — our own operator player id — so sessions resolve via [FindSessionByPlayerIdQuery]
 * (findByPlayerId, most-recent session for that player). Each request is authenticated by the
 * `X-Secret-Key` header, checked against the aggregator's stored secret.
 *
 * Money is integer minor units == wallet system units (nano), passed straight through. TONGame
 * currency is not session-locked — the player can switch in-game — so each call carries its own
 * currency, pinned onto the resolved session before driving the spin pipeline.
 *
 * `/debit` is the decline path: insufficient funds / limit breaches answer non-2xx (402), which
 * rolls back the engine's surrounding transaction.
 */
class TongameWebhook(
    private val bus: Bus,
    private val playerPort: IPlayerPort,
) {

    fun Route.route() = route("/tongame") {
        post("/player") {
            val body = call.receive<PlayerRequest>()
            call.handle {
                val session = resolveSession(call, body.playerId, currency = null)
                val player = playerPort.findPlayer(session.playerId)
                call.respond(PlayerResponse(username = player.username, profilePic = player.profilePic))
            }
        }

        post("/balance") {
            val body = call.receive<BalanceRequest>()
            call.handle {
                val session = resolveSession(call, body.playerId, body.currency)
                val balance = bus(FindSessionBalanceQuery(session))
                call.respond(BalanceResponse(balance = balance.total.value))
            }
        }

        post("/round/open") {
            val body = call.receive<RoundRequest>()
            call.handle {
                resolveSession(call, body.playerId, body.currency)
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/round/close") {
            val body = call.receive<RoundRequest>()
            call.handle {
                val session = resolveSession(call, body.playerId, body.currency)
                bus(EndRoundSessionCommand(session = session, externalRoundId = body.roundId.toString()))
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/debit") {
            val body = call.receive<MoneyRequest>()
            call.handle {
                val session = resolveSession(call, body.playerId, body.currency)
                bus(
                    PlaceSpinSessionCommand(
                        session = session,
                        externalRoundId = body.roundId.toString(),
                        externalSpinId = "${body.roundId}:place",
                        amount = Amount(body.amount),
                    )
                )
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/credit") {
            val body = call.receive<MoneyRequest>()
            call.handle {
                val session = resolveSession(call, body.playerId, body.currency)
                bus(
                    SettleSpinSessionCommand(
                        session = session,
                        externalRoundId = body.roundId.toString(),
                        externalSpinId = "${body.roundId}:settle",
                        amount = Amount(body.amount),
                    )
                )
                call.respond(HttpStatusCode.OK)
            }
        }
    }

    /** Resolve our session by the echoed playerId, verify the shared secret, and pin the request currency. */
    private suspend fun resolveSession(call: ApplicationCall, playerId: String, currency: String?): Session {
        val session = bus(FindSessionByPlayerIdQuery(playerId))
        val secret = TongameConfig(session.gameVariant.game.provider.aggregator.config).apiKey
        if (call.request.headers["X-Secret-Key"] != secret) throw InvalidSecretException()
        return if (currency != null) session.copy(currency = Currency(currency)) else session
    }

    private suspend fun ApplicationCall.handle(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: SessionNotFoundException) {
            respond(HttpStatusCode.Unauthorized, ErrorResponse("UNKNOWN_SESSION", e.message))
        } catch (e: InvalidSecretException) {
            respond(HttpStatusCode.Unauthorized, ErrorResponse("INVALID_SECRET", e.message))
        } catch (e: InsufficientBalanceException) {
            respond(HttpStatusCode.PaymentRequired, ErrorResponse("DECLINED", e.message))
        } catch (e: MaxPlaceSpinException) {
            respond(HttpStatusCode.PaymentRequired, ErrorResponse("DECLINED", e.message))
        } catch (e: ForbiddenException) {
            respond(HttpStatusCode.Conflict, ErrorResponse("REJECTED", e.message))
        } catch (e: NotFoundException) {
            respond(HttpStatusCode.Conflict, ErrorResponse("REJECTED", e.message))
        } catch (e: ConflictException) {
            respond(HttpStatusCode.Conflict, ErrorResponse("REJECTED", e.message))
        }
    }

    private class InvalidSecretException : RuntimeException("Invalid X-Secret-Key")

    @Serializable
    private data class PlayerRequest(val playerId: String)

    @Serializable
    private data class BalanceRequest(val playerId: String, val currency: String)

    @Serializable
    private data class RoundRequest(
        val playerId: String,
        val roundId: Long,
        val game: String? = null,
        val currency: String,
    )

    @Serializable
    private data class MoneyRequest(
        val playerId: String,
        val roundId: Long,
        val game: String? = null,
        val currency: String,
        val amount: Long,
    )

    @Serializable
    private data class PlayerResponse(val username: String, val profilePic: String?)

    @Serializable
    private data class BalanceResponse(val balance: Long)

    @Serializable
    private data class ErrorResponse(val error: String, val message: String?)
}
