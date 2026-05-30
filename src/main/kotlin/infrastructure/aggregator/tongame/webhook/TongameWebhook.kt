package infrastructure.aggregator.tongame.webhook

import application.Bus
import application.command.session.EndRoundSessionCommand
import application.command.session.PlaceSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.port.external.IPlayerPort
import application.port.external.IWalletPort
import application.query.aggregator.FindAggregatorQuery
import application.query.session.FindSessionQuery
import domain.exception.conflict.ConflictException
import domain.exception.forbidden.ForbiddenException
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.forbidden.MaxPlaceSpinException
import domain.exception.notfound.NotFoundException
import domain.exception.notfound.SessionNotFoundException
import domain.model.Session
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.Identity
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
 * Identity is the `sessionToken` we minted and sent to `POST /api/v1/session`, which the provider
 * embeds in the launch URL and echoes back in every call. Every route resolves our exact session
 * via [FindSessionQuery] (findByToken) and reads the player from it — `/player`/`/balance` for the
 * profile/wallet, `/debit`/`/credit`/`/round/close` to attach spins to a session-keyed round. Every
 * call authenticates with the `X-Secret-Key` header, checked against the TONGame aggregator's stored
 * `apiKey` (a constant, read from the aggregator config — not from any session).
 *
 * Money is integer minor units == wallet system units (nano), passed straight through. TONGame
 * currency is not session-locked — the player can switch in-game — so each call carries its own
 * currency, used directly (balance) or pinned onto the resolved session (spins).
 *
 * `/debit` is the decline path: insufficient funds / limit breaches answer non-2xx (402).
 */
class TongameWebhook(
    private val bus: Bus,
    private val playerPort: IPlayerPort,
    private val walletPort: IWalletPort,
) {

    fun Route.route() = route("/tongame") {
        post("/player") {
            val body = call.receive<PlayerRequest>()
            call.handle {
                call.verifySecret()
                val session = bus(FindSessionQuery(body.sessionToken))
                val player = playerPort.findPlayer(session.playerId)
                call.respond(
                    PlayerResponse(
                        id = session.playerId.value,
                        username = player.username,
                        profilePic = player.profilePic,
                    )
                )
            }
        }

        post("/balance") {
            val body = call.receive<BalanceRequest>()
            call.handle {
                call.verifySecret()
                val session = bus(FindSessionQuery(body.sessionToken))
                val balance = walletPort.findBalance(session.playerId, Currency(body.currency))
                call.respond(BalanceResponse(balance = balance.total.value))
            }
        }

        post("/round/open") {
            call.receive<RoundRequest>()
            call.handle {
                call.verifySecret()
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/round/close") {
            val body = call.receive<RoundRequest>()
            call.handle {
                call.verifySecret()
                val session = resolveSession(body.sessionToken, body.currency)
                bus(EndRoundSessionCommand(session = session, externalRoundId = body.roundId.toString()))
                call.respond(HttpStatusCode.OK)
            }
        }

        post("/debit") {
            val body = call.receive<MoneyRequest>()
            call.handle {
                call.verifySecret()
                val session = resolveSession(body.sessionToken, body.currency)
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
                call.verifySecret()
                val session = resolveSession(body.sessionToken, body.currency)
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

    /** Verify X-Secret-Key against the TONGame aggregator's stored apiKey (a constant, not session-derived). */
    private suspend fun ApplicationCall.verifySecret() {
        val aggregator = bus(FindAggregatorQuery(Identity(AGGREGATOR_IDENTITY)))
            .orElseThrow { InvalidSecretException() }
        val apiKey = TongameConfig(aggregator.config).apiKey
        if (request.headers["X-Secret-Key"] != apiKey) throw InvalidSecretException()
    }

    /** Resolve the exact session by its token (the provider echoes our own token) and pin the request currency. */
    private suspend fun resolveSession(sessionToken: String, currency: String): Session =
        bus(FindSessionQuery(sessionToken)).copy(currency = Currency(currency))

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
    private data class PlayerRequest(val sessionToken: String)

    @Serializable
    private data class BalanceRequest(val sessionToken: String, val currency: String)

    @Serializable
    private data class RoundRequest(
        val sessionToken: String,
        val roundId: Long,
        val game: String? = null,
        val currency: String,
    )

    @Serializable
    private data class MoneyRequest(
        val sessionToken: String,
        val roundId: Long,
        val game: String? = null,
        val currency: String,
        val amount: Long,
    )

    @Serializable
    private data class PlayerResponse(val id: String, val username: String, val profilePic: String?)

    @Serializable
    private data class BalanceResponse(val balance: Long)

    @Serializable
    private data class ErrorResponse(val error: String, val message: String?)

    private companion object {
        const val AGGREGATOR_IDENTITY = "tongame"
    }
}
