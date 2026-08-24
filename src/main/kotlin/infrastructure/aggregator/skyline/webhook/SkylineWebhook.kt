package infrastructure.aggregator.skyline.webhook

import application.Bus
import application.command.session.EndCasinoRoundSessionCommand
import application.command.session.PlaceSpinCasinoSessionCommand
import application.command.session.RollbackSpinCasinoSessionCommand
import application.command.session.SettleSpinCasinoSessionCommand
import application.port.external.ICurrencyPort
import application.port.external.IWebhookGuardPort
import application.query.aggregator.FindAggregatorQuery
import application.query.session.FindCasinoSessionBalanceQuery
import application.query.session.FindCasinoSessionQuery
import domain.exception.DomainException
import domain.exception.forbidden.InsufficientBalanceException
import domain.model.PlayerBalance
import domain.model.CasinoSession
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.Identity
import infrastructure.aggregator.skyline.SkylineAction
import infrastructure.aggregator.skyline.SkylineAdapterProvider
import infrastructure.aggregator.skyline.SkylineConfig
import infrastructure.aggregator.skyline.client.SkylineJwt
import infrastructure.aggregator.skyline.webhook.dto.SkylineGetBalanceRequest
import infrastructure.aggregator.skyline.webhook.dto.SkylineUpdateBalanceRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlin.math.roundToLong
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * Inbound Skyline wallet callbacks (provider → operator): one POST at `/api/webhook/skyline`
 * serving `get_balance` and `update_balance`, told apart by the `action` field of the payload.
 *
 * The body is a bare HS256 JWT and so is our answer. That signature is the ONLY authentication a
 * callback carries — the vendor sends no api key inbound — so nothing is parsed, and no session is
 * touched, until it verifies. Demo play never reaches here: the vendor makes no callback for it.
 *
 * Their scheme has no nonce and no timestamp, so replay safety comes entirely from `transaction`.
 * That one value is reused across all three of the vendor's retries AND across the refund they send
 * five minutes later, which is what lets the spin ids derived from it stay stable through both.
 *
 * Money on the wire is an integer number of minor units. Everything answers HTTP 200 — an error is
 * a code in the payload, per their contract, not a status line.
 *
 * The aggregator row must be registered under identity [AGGREGATOR_IDENTITY]; its config supplies
 * the signing secret.
 */
class SkylineWebhook(
    private val bus: Bus,
    private val currencyPort: ICurrencyPort,
    private val guardPort: IWebhookGuardPort,
) {

    private val logger = LoggerFactory.getLogger(SkylineWebhook::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun Route.route() = post("/skyline") {
        val raw = call.receiveText()

        val config = loadConfig()
        if (config == null) {
            logger.warn("Skyline webhook rejected: aggregator {} is not configured", AGGREGATOR_IDENTITY)
            call.respondPayload(config = null, payload = error(ERROR_GENERAL, "aggregator not configured"))
            return@post
        }

        val payload = SkylineJwt(config.jwtSecret).decode(raw)
        if (payload == null) {
            logger.warn("Skyline webhook rejected: body is not a token signed with our secret")
            call.respondPayload(config, error(ERROR_GENERAL, "invalid signature"))
            return@post
        }

        val response = try {
            when (val action = payload[FIELD_ACTION]?.jsonPrimitive?.contentOrNull) {
                SkylineAction.GET_BALANCE -> getBalance(payload.decode(SkylineGetBalanceRequest.serializer()))
                SkylineAction.UPDATE_BALANCE -> updateBalance(payload.decode(SkylineUpdateBalanceRequest.serializer()))
                else -> {
                    logger.warn("Skyline webhook rejected: unsupported action {}", action)
                    error(ERROR_GENERAL, "unsupported action")
                }
            }
        } catch (e: InsufficientBalanceException) {
            error(ERROR_INSUFFICIENT_FUNDS, "insufficient funds")
        } catch (e: DomainException) {
            logger.warn("Skyline callback refused: {}", e.message)
            error(ERROR_GENERAL, e.message.orEmpty())
        }

        call.respondPayload(config, response)
    }

    /**
     * Read under the same lock a money call takes: a call that both takes a bet and pays a win
     * moves the wallet twice, and a balance read between the two legs reports one that reflects
     * half of somebody else's transaction.
     */
    private suspend fun getBalance(body: SkylineGetBalanceRequest): JsonObject {
        val session = resolveSession(body.session)

        return guardPort.withLock(session.walletKey()) {
            bus(FindCasinoSessionBalanceQuery(session)).toPayload(session.currency)
        }
    }

    /**
     * A bet, a win, both, or the refund of a transaction we already booked.
     *
     * The bet is dispatched first: it is the only leg that can be declined, so netting it against a
     * win would let a player stake money the wallet never held. Today's game sends the two legs as
     * separate calls, but the vendor states future games will combine them, so both are handled.
     *
     * A redelivery costs nothing — the spin handlers recognise a committed spin by its external id
     * and answer with the balance as it stands — and the wallet lock is what makes that check safe
     * when two copies of the same call arrive at once.
     */
    private suspend fun updateBalance(body: SkylineUpdateBalanceRequest): JsonObject {
        val session = resolveSession(body.session)

        return guardPort.withLock(session.walletKey()) {
            if (body.isRefund) return@withLock refund(session, body)

            if (guardPort.isRolledBack(transactionKey(body.transaction))) {
                logger.warn("Skyline transaction {} arrived after its refund", body.transaction)
                return@withLock error(ERROR_GENERAL, "transaction already refunded")
            }

            // Only some of their games group transactions into a round; for the rest each
            // transaction is its own.
            val roundId = body.round?.ifBlank { null } ?: body.transaction
            val currency = session.currency

            var balance: PlayerBalance? = null

            // A free round has no stake to charge, and the engine's own free-round path leaves the
            // balance alone — but the PLACE still has to happen, because it is what opens the round
            // against the grant and spends one of its rounds. A `bonus` call that already carries a
            // win is the payout leg of a round whose stake leg came separately, so it settles only.
            if (body.betAmount > 0 || (body.bonus != null && body.winAmount == 0L)) {
                balance = bus(
                    PlaceSpinCasinoSessionCommand(
                        session = session,
                        externalRoundId = roundId,
                        externalSpinId = body.transaction.placeSpinId(),
                        freespinId = body.bonus,
                        amount = body.betAmount.toSystemUnits(currency),
                    )
                )
            }

            if (body.winAmount > 0) {
                balance = bus(
                    SettleSpinCasinoSessionCommand(
                        session = session,
                        externalRoundId = roundId,
                        externalSpinId = body.transaction.settleSpinId(),
                        freespinId = body.bonus,
                        amount = body.winAmount.toSystemUnits(currency),
                    )
                )
            }

            // A zero/zero call is a legal round marker; answer with the balance as it stands.
            val current = balance ?: bus(FindCasinoSessionBalanceQuery(session))

            if (body.isLast) closeRound(session, roundId)

            current.toPayload(currency, transaction = body.transaction)
        }
    }

    /**
     * Reverses the transaction named by [SkylineUpdateBalanceRequest.transaction] — the original
     * id, not a new one. Repeating it is safe: the rollback spins are derived from the ids they
     * reverse, so a second attempt finds them already booked and moves nothing.
     */
    private suspend fun refund(session: CasinoSession, body: SkylineUpdateBalanceRequest): JsonObject {
        // Marked before anything is reversed: the vendor may refund a call we never received, and
        // that call must then be refused on arrival rather than executed and orphaned.
        guardPort.markRolledBack(key = transactionKey(body.transaction), ttlSeconds = REFUND_TTL_SECONDS)

        val balance = bus(
            RollbackSpinCasinoSessionCommand(
                session = session,
                // Win first, then bet: reclaiming before refunding keeps the balance non-negative.
                externalSpinIds = listOf(
                    body.transaction.settleSpinId(),
                    body.transaction.placeSpinId(),
                ),
            )
        )

        return balance.toPayload(session.currency, transaction = body.transaction)
    }

    /** The money has already moved by this point, so a round that cannot be closed must not fail
     *  the call — the vendor would refund a transaction that legitimately went through. */
    private suspend fun closeRound(session: CasinoSession, roundId: String) {
        runCatching { bus(EndCasinoRoundSessionCommand(session = session, externalRoundId = roundId)) }
            .onFailure { logger.warn("Skyline round {} not closed: {}", roundId, it.message) }
    }

    /**
     * The session is resolved by the token WE minted and handed to `game_launch`; the vendor echoes
     * it back on every callback and mints nothing of its own.
     */
    private suspend fun resolveSession(token: String): CasinoSession {
        val session = bus(FindCasinoSessionQuery(token))

        // Our tokens are shared across every integration, so a token could name a session opened
        // elsewhere. A session that is not theirs is no session of theirs.
        if (session.gameVariant.integration != SkylineAdapterProvider.INTEGRATION) {
            logger.warn("Skyline callback for session opened on {}", session.gameVariant.integration)
            throw domain.exception.notfound.CasinoSessionNotFoundException()
        }

        return session
    }

    /** The resource a money call contends on is the wallet account, not the session or the game. */
    private fun CasinoSession.walletKey(): String = "$AGGREGATOR_IDENTITY:wallet:${playerId.value}:${currency.value}"

    private fun transactionKey(transaction: String): String = "$AGGREGATOR_IDENTITY:$transaction"

    private fun String.placeSpinId(): String = "$this$PLACE_SUFFIX"

    private fun String.settleSpinId(): String = "$this$SETTLE_SUFFIX"

    /** CasinoProvider minor units → wallet system units (nano). */
    private suspend fun Long.toSystemUnits(currency: Currency): Amount =
        Amount(currencyPort.convertToUnits(this / MINOR_UNITS_PER_MAJOR, currency))

    private suspend fun Amount.toMinorUnits(currency: Currency): Long =
        (currencyPort.convertFromUnits(value, currency) * MINOR_UNITS_PER_MAJOR).roundToLong()

    /**
     * `balance` is the whole spendable wallet, bonus included — it is what the game displays and
     * what our own affordability check actually measures, so reporting only the real part would
     * show a player less than we are willing to accept a bet for. `bonus_balance` breaks out how
     * much of it is bonus money; it is a component of `balance`, not an addition to it.
     */
    private suspend fun PlayerBalance.toPayload(currency: Currency, transaction: String? = null): JsonObject =
        buildJsonObject {
            put(FIELD_BALANCE, total.toMinorUnits(currency))
            put(FIELD_BONUS_BALANCE, bonusAmount.toMinorUnits(currency))
            put(FIELD_CURRENCY, currency.value)
            if (transaction != null) put(FIELD_TRANSACTION, transaction)
        }

    /** [ERROR_INSUFFICIENT_FUNDS] is what raises the "not enough funds" prompt in their games;
     *  everything else we can fail on is [ERROR_GENERAL]. */
    private fun error(code: Int, description: String): JsonObject = buildJsonObject {
        put(FIELD_ERROR, code)
        put(FIELD_DESCRIPTION, description)
    }

    private fun <T> JsonObject.decode(serializer: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(serializer, this)

    private suspend fun loadConfig(): SkylineConfig? =
        bus(FindAggregatorQuery(Identity(AGGREGATOR_IDENTITY)))
            .map { SkylineConfig(it.config) }
            .orElse(null)

    /**
     * Answers in the shape the request arrived in: a signed token, wrapped in `result` exactly as
     * their own API wraps its answers. A payload that cannot be signed — no config, so no secret —
     * goes out as plain JSON, which is all a caller we could not authenticate is owed.
     */
    private suspend fun ApplicationCall.respondPayload(config: SkylineConfig?, payload: JsonObject) {
        val envelope = buildJsonObject { put(FIELD_RESULT, payload) }

        val body = config
            ?.takeIf { it.jwtSecret.isNotBlank() }
            ?.let { SkylineJwt(it.jwtSecret).encode(envelope) }
            ?: envelope.toString()

        respondText(text = body, contentType = ContentType.Application.Json, status = HttpStatusCode.OK)
    }

    companion object {
        /** The aggregator row this webhook reads its signing secret from. */
        const val AGGREGATOR_IDENTITY = "skyline"

        /** The player cannot cover the bet — the code that raises their in-game prompt. */
        const val ERROR_INSUFFICIENT_FUNDS = 105

        /** Everything else we can refuse on. */
        const val ERROR_GENERAL = 106

        private const val FIELD_ACTION = "action"

        private const val FIELD_RESULT = "result"

        private const val FIELD_BALANCE = "balance"

        private const val FIELD_BONUS_BALANCE = "bonus_balance"

        private const val FIELD_CURRENCY = "currency"

        private const val FIELD_TRANSACTION = "transaction"

        private const val FIELD_ERROR = "error"

        private const val FIELD_DESCRIPTION = "description"

        private const val PLACE_SUFFIX = ":place"

        private const val SETTLE_SUFFIX = ":settle"

        private const val MINOR_UNITS_PER_MAJOR = 100.0

        /** The vendor refunds five minutes after a call it could not complete, and may retry the
         *  refund itself; the marker has to outlive that window by a wide margin. */
        private const val REFUND_TTL_SECONDS = 25L * 60 * 60
    }
}
