package infrastructure.aggregator.gambutsoft.webhook

import application.Bus
import application.command.session.EndCasinoRoundSessionCommand
import application.command.session.PlaceSpinCasinoSessionCommand
import application.command.session.RollbackSpinCasinoSessionCommand
import application.command.session.SettleSpinCasinoSessionCommand
import application.port.external.IWebhookGuardPort
import application.query.aggregator.FindAggregatorQuery
import application.query.session.FindCasinoSessionBalanceQuery
import application.query.session.FindCasinoSessionByExternalTokenQuery
import domain.exception.DomainException
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.forbidden.MaxPlaceSpinException
import domain.exception.notfound.CasinoSessionNotFoundException
import domain.model.PlayerBalance
import domain.model.CasinoSession
import domain.vo.Amount
import domain.vo.Identity
import infrastructure.aggregator.gambutsoft.GambutsoftAdapterProvider
import infrastructure.aggregator.gambutsoft.GambutsoftConfig
import infrastructure.aggregator.gambutsoft.GambutsoftMoney
import infrastructure.aggregator.gambutsoft.client.GambutsoftSigner
import infrastructure.aggregator.gambutsoft.webhook.dto.CallbackResponse
import infrastructure.aggregator.gambutsoft.webhook.dto.GetBalanceRequest
import infrastructure.aggregator.gambutsoft.webhook.dto.RollbackRequest
import infrastructure.aggregator.gambutsoft.webhook.dto.WriteBetRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Inbound Gambutsoft wallet callbacks (provider → operator). One POST at
 * `/api/webhook/gambutsoft` serving `getBalance`, `writeBet` and `rollback`, told apart by the
 * `cmd` field of the body — the vendor configures a single callback URL per currency.
 *
 * Every request is authenticated by `X-Signature` over the raw body, which is why the body is read
 * as text and only parsed afterwards. There is no nonce and no timestamp in their scheme, so the
 * signature alone does not stop a replay; `transactionId` does, through the spin ids derived from
 * it.
 *
 * One `writeBet` may carry a bet and a win at once, which is two wallet moves: the whole call is
 * held under the player's wallet lock so no concurrent call can observe half of it, and the bet is
 * always applied first because it is the only leg that can be declined. Money on the wire is in
 * major units.
 *
 * Their contract reads HTTP 2xx + `status: "success"` as accepted and anything else as rejected
 * and retryable, so a decline answers HTTP 400 with the balance left untouched.
 *
 * The aggregator row must be registered under identity [AGGREGATOR_IDENTITY]; its config supplies
 * the signing key.
 */
class GambutsoftWebhook(
    private val bus: Bus,
    private val guardPort: IWebhookGuardPort,
) {

    private val logger = LoggerFactory.getLogger(GambutsoftWebhook::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val responseJson = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun Route.route() = post("/gambutsoft") {
        val raw = call.receiveText()

        val config = loadConfig()
        if (config == null) {
            logger.warn("Gambutsoft webhook rejected: aggregator {} is not configured", AGGREGATOR_IDENTITY)
            call.respondCallback(failure(login = "", error = ERROR_NOT_CONFIGURED))
            return@post
        }

        val signature = call.request.headers[HEADER_SIGNATURE]
        if (!GambutsoftSigner(config.secretKey).verify(raw, signature)) {
            logger.warn("Gambutsoft webhook rejected: invalid signature")
            call.respondCallback(failure(login = "", error = ERROR_INVALID_SIGNATURE))
            return@post
        }

        // Parsed only after the signature holds, so a malformed body can only ever come from a
        // caller that knows the key.
        val request = json.parseToJsonElement(raw).jsonObject
        val login = request[FIELD_LOGIN]?.jsonPrimitive?.contentOrNull.orEmpty()

        val response = try {
            when (val cmd = request[FIELD_CMD]?.jsonPrimitive?.contentOrNull) {
                CMD_GET_BALANCE -> getBalance(request.decode(GetBalanceRequest.serializer()))
                CMD_WRITE_BET -> writeBet(request.decode(WriteBetRequest.serializer()))
                CMD_ROLLBACK -> rollback(request.decode(RollbackRequest.serializer()))
                else -> {
                    logger.warn("Gambutsoft webhook rejected: unsupported command {}", cmd)
                    failure(login, ERROR_UNSUPPORTED_COMMAND)
                }
            }
        } catch (e: CasinoSessionNotFoundException) {
            failure(login, ERROR_SESSION_NOT_FOUND)
        } catch (e: DomainException) {
            logger.warn("Gambutsoft callback refused for login {}: {}", login, e.message)
            failure(login, e.message.orEmpty())
        }

        call.respondCallback(response)
    }

    /** Read under the same lock a money call takes: a `writeBet` carrying both a bet and a win
     *  moves the wallet twice, and a balance read between the two legs reports one that reflects
     *  half of a transaction. */
    private suspend fun getBalance(body: GetBalanceRequest): CallbackResponse {
        val session = resolveSession(body.sessionid, body.login)

        return guardPort.withLock(session.walletKey()) {
            success(session, body.login, bus(FindCasinoSessionBalanceQuery(session)))
        }
    }

    /**
     * A bet, a win, or both. The bet is dispatched first: it is the only leg with an affordability
     * check, and the vendor states outright that it does not reserve funds off `getBalance`, so a
     * win credited ahead of it would fund a bet the player could not otherwise place.
     *
     * A redelivery costs nothing — the spin handlers recognise a committed spin by its external id
     * and answer with the balance as it stands instead of moving money twice — and the wallet lock
     * is what makes that check safe when two copies of the same callback arrive at once.
     */
    private suspend fun writeBet(body: WriteBetRequest): CallbackResponse {
        val session = resolveSession(body.sessionid, body.login)

        return guardPort.withLock(session.walletKey()) {
            declining(session, body.login) {
                if (guardPort.isRolledBack(transactionKey(body.transactionId))) {
                    return@declining declined(session, body.login, ERROR_ALREADY_ROLLED_BACK)
                }

                val bet = amount(body.bet)
                val win = amount(body.win)

                // Only some sub-providers group transactions into a round; for the rest each
                // transaction is its own round.
                val roundId = body.roundId ?: body.transactionId

                var balance: PlayerBalance? = null

                if (bet > Amount.ZERO) {
                    balance = bus(
                        PlaceSpinCasinoSessionCommand(
                            session = session,
                            externalRoundId = roundId,
                            externalSpinId = body.transactionId.placeSpinId(),
                            amount = bet,
                        )
                    )
                }

                if (win > Amount.ZERO) {
                    balance = bus(
                        SettleSpinCasinoSessionCommand(
                            session = session,
                            externalRoundId = roundId,
                            externalSpinId = body.transactionId.settleSpinId(),
                            amount = win,
                        )
                    )
                }

                // A zero/zero call is a legal round marker; answer with the balance as it stands.
                val current = balance ?: bus(FindCasinoSessionBalanceQuery(session))

                if (body.roundFinished) closeRound(session, roundId)

                success(session, body.login, current)
            }
        }
    }

    /**
     * Reverses the bet named by [RollbackRequest.transactionId] — the id of the original
     * transaction, not a new one. Repeating it is safe and answers success: the rollback spins are
     * derived from the ids they reverse, so a second attempt finds them already booked and moves
     * nothing.
     */
    private suspend fun rollback(body: RollbackRequest): CallbackResponse {
        val session = resolveSession(body.sessionid, body.login)

        return guardPort.withLock(session.walletKey()) {
            // Marked before anything is reversed: the vendor may send the rollback ahead of the
            // transaction it reverses, and that transaction must then be refused on arrival rather
            // than executed and orphaned.
            guardPort.markRolledBack(key = transactionKey(body.transactionId), ttlSeconds = ROLLBACK_TTL_SECONDS)

            declining(session, body.login) {
                val balance = bus(
                    RollbackSpinCasinoSessionCommand(
                        session = session,
                        // Win first, then bet: reclaiming before refunding keeps the balance non-negative.
                        externalSpinIds = listOf(
                            body.transactionId.settleSpinId(),
                            body.transactionId.placeSpinId(),
                        ),
                    )
                )

                success(session, body.login, balance)
            }
        }
    }

    /**
     * Turns a refused money call into the answer the vendor expects: a rejection that still carries
     * the player's real balance and currency. Reached only once the session is known — before that
     * there is no balance to report and no currency to name.
     */
    private suspend fun declining(
        session: CasinoSession,
        login: String,
        block: suspend () -> CallbackResponse,
    ): CallbackResponse = try {
        block()
    } catch (e: InsufficientBalanceException) {
        declined(session, login, ERROR_INSUFFICIENT_FUNDS)
    } catch (e: MaxPlaceSpinException) {
        declined(session, login, ERROR_BET_LIMIT_EXCEEDED)
    } catch (e: DomainException) {
        logger.warn("Gambutsoft call refused for session {}: {}", session.externalToken, e.message)
        declined(session, login, e.message.orEmpty())
    }

    /** The money has already moved by this point, so a round that cannot be closed must not fail
     *  the call — the vendor would roll back a transaction that legitimately went through. */
    private suspend fun closeRound(session: CasinoSession, roundId: String) {
        runCatching { bus(EndCasinoRoundSessionCommand(session = session, externalRoundId = roundId)) }
            .onFailure { logger.warn("Gambutsoft round {} not closed: {}", roundId, it.message) }
    }

    /**
     * The session is resolved by the id the PROVIDER minted at launch, stored as
     * `CasinoSession.externalToken`; it is the only identifier their callbacks carry. `login`
     * cannot resolve anything on its own — one player has many sessions — so it is only checked
     * against the session we found.
     */
    private suspend fun resolveSession(sessionId: String, login: String): CasinoSession {
        val session = bus(FindCasinoSessionByExternalTokenQuery(sessionId))

        // Their session ids are short counters, so a value could collide with an id another
        // aggregator minted. A session opened elsewhere is no session of theirs.
        if (session.gameVariant.integration != GambutsoftAdapterProvider.INTEGRATION) {
            logger.warn("Gambutsoft callback for session {} opened on {}", sessionId, session.gameVariant.integration)
            throw CasinoSessionNotFoundException()
        }

        if (login.isNotBlank() && login != session.playerId.value) {
            logger.warn(
                "Gambutsoft login {} does not match session {} player {}",
                login, sessionId, session.playerId.value,
            )
        }

        return session
    }

    /** The resource a money call contends on is the wallet account, not the session or the game. */
    private fun CasinoSession.walletKey(): String = "$AGGREGATOR_IDENTITY:wallet:${playerId.value}:${currency.value}"

    private fun transactionKey(transactionId: String): String = "$AGGREGATOR_IDENTITY:$transactionId"

    private fun String.placeSpinId(): String = "$this$PLACE_SUFFIX"

    private fun String.settleSpinId(): String = "$this$SETTLE_SUFFIX"

    /** Absent means zero — a callback carrying only a win omits the bet and vice versa, and some
     *  senders spell the absence as an explicit null. A negative value is refused by [Amount]
     *  itself, which is the only sign the vendor may send. */
    private fun amount(value: JsonPrimitive?): Amount = value
        ?.takeIf { it !is JsonNull }
        ?.content
        ?.takeIf { it.isNotBlank() }
        ?.let { GambutsoftMoney.toAmount(it) }
        ?: Amount.ZERO

    private fun success(session: CasinoSession, login: String, balance: PlayerBalance) = CallbackResponse(
        balance = GambutsoftMoney.toJsonNumber(balance.total),
        currency = session.currency.value,
        error = "",
        login = login,
        status = STATUS_SUCCESS,
    )

    /** A refused money call still reports the real balance: the game shows the player what it was
     *  told, and a zero here reads as an empty wallet. */
    private suspend fun declined(session: CasinoSession, login: String, error: String) = CallbackResponse(
        balance = GambutsoftMoney.toJsonNumber(bus(FindCasinoSessionBalanceQuery(session)).total),
        currency = session.currency.value,
        error = error,
        login = login,
        status = STATUS_FAIL,
    )

    /** Used when no session was resolved, so no balance may be disclosed. */
    private fun failure(login: String, error: String) = CallbackResponse(
        balance = GambutsoftMoney.toJsonNumber(Amount.ZERO),
        currency = "",
        error = error,
        login = login,
        status = STATUS_FAIL,
    )

    private fun <T> JsonObject.decode(serializer: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(serializer, this)

    private suspend fun loadConfig(): GambutsoftConfig? =
        bus(FindAggregatorQuery(Identity(AGGREGATOR_IDENTITY)))
            .map { GambutsoftConfig(it.config) }
            .orElse(null)

    private suspend fun ApplicationCall.respondCallback(body: CallbackResponse) {
        respondText(
            text = responseJson.encodeToString(CallbackResponse.serializer(), body),
            contentType = ContentType.Application.Json,
            status = if (body.status == STATUS_SUCCESS) HttpStatusCode.OK else HttpStatusCode.BadRequest,
        )
    }

    companion object {
        /** The aggregator row this webhook reads its signing key from. */
        const val AGGREGATOR_IDENTITY = "gambutsoft"

        private const val CMD_GET_BALANCE = "getBalance"

        private const val CMD_WRITE_BET = "writeBet"

        private const val CMD_ROLLBACK = "rollback"

        private const val FIELD_CMD = "cmd"

        private const val FIELD_LOGIN = "login"

        private const val HEADER_SIGNATURE = "X-Signature"

        private const val PLACE_SUFFIX = ":place"

        private const val SETTLE_SUFFIX = ":settle"

        private const val STATUS_SUCCESS = "success"

        private const val STATUS_FAIL = "fail"

        private const val ERROR_NOT_CONFIGURED = "aggregator not configured"

        private const val ERROR_INVALID_SIGNATURE = "invalid signature"

        private const val ERROR_UNSUPPORTED_COMMAND = "unsupported command"

        private const val ERROR_SESSION_NOT_FOUND = "session not found"

        private const val ERROR_INSUFFICIENT_FUNDS = "insufficient funds"

        private const val ERROR_BET_LIMIT_EXCEEDED = "bet limit exceeded"

        private const val ERROR_ALREADY_ROLLED_BACK = "transaction already rolled back"

        /** The vendor retries a rollback well past the transaction it reverses, so the marker has
         *  to outlive that window. */
        private const val ROLLBACK_TTL_SECONDS = 25L * 60 * 60
    }
}
