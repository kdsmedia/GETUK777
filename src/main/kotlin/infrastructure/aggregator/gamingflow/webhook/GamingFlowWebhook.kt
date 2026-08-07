package infrastructure.aggregator.gamingflow.webhook

import application.Bus
import application.command.session.EndRoundSessionCommand
import application.command.session.PlaceSpinSessionCommand
import application.command.session.RollbackSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.port.external.ICurrencyPort
import application.port.external.IWebhookGuardPort
import application.query.aggregator.FindAggregatorQuery
import application.query.session.FindSessionBalanceQuery
import application.query.session.FindSessionQuery
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.forbidden.MaxPlaceSpinException
import domain.exception.notfound.SessionNotFoundException
import domain.model.PlayerBalance
import domain.model.Session
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.Identity
import infrastructure.aggregator.gamingflow.GamingFlowConfig
import infrastructure.aggregator.gamingflow.client.GamingFlowSigner
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcError
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcRequest
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcResponse
import infrastructure.aggregator.gamingflow.webhook.dto.BalanceResult
import infrastructure.aggregator.gamingflow.webhook.dto.GetBalanceParams
import infrastructure.aggregator.gamingflow.webhook.dto.RollbackTransactionParams
import infrastructure.aggregator.gamingflow.webhook.dto.TransactionResult
import infrastructure.aggregator.gamingflow.webhook.dto.WithdrawAndDepositParams
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import kotlin.math.roundToLong

/**
 * Seamless API v2 (provider → operator). One JSON-RPC endpoint at `/api/webhook/gamingflow` serving
 * `getBalance`, `withdrawAndDeposit` and `rollbackTransaction`.
 *
 * Authentication is the mirror image of our outbound calls: the provider signs each request with the
 * shared key and we answer signed with the very same nonce and timestamp. Three things must all hold
 * or the request never reaches a handler — subject match, a timestamp inside the freshness window,
 * and a nonce that has not been claimed before. The nonce check is what makes a captured request
 * unusable a second time; without it the signature buys nothing.
 *
 * `withdrawAndDeposit` is a bet and a win in a single call, which maps onto a PLACE and a SETTLE spin
 * derived from one `transactionRef`. Those ids are what make redelivery safe: the spin handlers
 * already recognise a committed spin and answer with the current balance instead of moving money
 * twice.
 *
 * Free rounds are not served here. The provider keeps only a bonus id and expects the operator to own
 * the remaining-round counter, which casino-engine has no store for — so a call that tries to charge
 * one is refused rather than silently billed as a normal bet.
 *
 * The aggregator row must be registered under identity [AGGREGATOR_IDENTITY]; its config supplies the
 * signing key and casino id.
 */
class GamingFlowWebhook(
    private val bus: Bus,
    private val currencyPort: ICurrencyPort,
    private val guardPort: IWebhookGuardPort,
) {

    private val logger = LoggerFactory.getLogger(GamingFlowWebhook::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun Route.route() = post("/gamingflow") {
        val body = call.receiveText()

        val config = loadConfig()
        if (config == null) {
            call.reject("Aggregator not configured")
            return@post
        }

        val nonce = call.request.headers[HEADER_NONCE]
        val timestamp = call.request.headers[HEADER_TIMESTAMP]?.toLongOrNull()
        val subject = call.request.headers[HEADER_SUBJECT]
        val signature = call.request.headers[HEADER_SIGNATURE]

        if (nonce == null || timestamp == null) {
            call.reject("Missing signature headers")
            return@post
        }
        if (subject != config.subject) {
            call.reject("Invalid signature subject")
            return@post
        }
        if (!isFresh(timestamp)) {
            call.reject("Invalid signature timestamp")
            return@post
        }

        val signer = GamingFlowSigner(keyId = config.keyId, keyValue = config.keyValue)
        if (!signer.verify(body = body, nonce = nonce, timestamp = timestamp, signature = signature)) {
            call.reject("Invalid signature")
            return@post
        }

        // After the signature, before any side effect: a replayed request is bit-identical and would
        // otherwise be indistinguishable from the original.
        if (!guardPort.claimNonce(key = "$AGGREGATOR_IDENTITY:$nonce", ttlSeconds = NONCE_TTL_SECONDS)) {
            call.reject("Nonce already used")
            return@post
        }

        val request = runCatching {
            json.decodeFromString(GamingFlowRpcRequest.serializer(), body)
        }.getOrNull()

        val response = if (request == null) {
            GamingFlowRpcResponse(id = 0, error = GamingFlowRpcError(PARSE_ERROR, "Parse error"))
        } else {
            GamingFlowRpcResponse(id = request.id, result = null).let { _ ->
                dispatch(request.method, request.params).fold(
                    onSuccess = { GamingFlowRpcResponse(id = request.id, result = it) },
                    onFailure = { GamingFlowRpcResponse(id = request.id, error = it.toRpcError(request.method)) }
                )
            }
        }

        call.respondSigned(config, signer, nonce, timestamp, response)
    }

    private suspend fun dispatch(method: String, params: JsonObject): Result<JsonElement> = runCatching {
        when (method) {
            METHOD_GET_BALANCE -> getBalance(params.decode(GetBalanceParams.serializer()))
            METHOD_WITHDRAW_AND_DEPOSIT -> withdrawAndDeposit(params.decode(WithdrawAndDepositParams.serializer()))
            METHOD_ROLLBACK -> rollbackTransaction(params.decode(RollbackTransactionParams.serializer()))
            // collectBonusReward belongs to the free-round flow we do not serve.
            else -> throw SeamlessException(METHOD_NOT_FOUND, "Method $method is not supported")
        }
    }

    private suspend fun getBalance(params: GetBalanceParams): JsonElement {
        val session = resolveSession(params.sessionAlternativeId, params.currency)
        val balance = bus(FindSessionBalanceQuery(session))

        // freeroundsLeft is deliberately absent — see the class comment.
        return json.encodeToJsonElement(
            BalanceResult.serializer(),
            BalanceResult(balance = balance.toMinorUnits(session.currency))
        )
    }

    private suspend fun withdrawAndDeposit(params: WithdrawAndDepositParams): JsonElement {
        if ((params.chargeFreerounds ?: 0) > 0) {
            throw SeamlessException(ERR_UNKNOWN, "Free rounds are not enabled for this integration")
        }
        if (params.deposit < 0) throw SeamlessException(ERR_NEGATIVE_DEPOSIT, "Negative deposit")
        if (params.withdraw < 0) throw SeamlessException(ERR_NEGATIVE_WITHDRAWAL, "Negative withdrawal")

        val session = resolveSession(params.sessionAlternativeId, params.currency)

        if (guardPort.isRolledBack("$AGGREGATOR_IDENTITY:${params.transactionRef}")) {
            throw SeamlessException(
                ERR_UNKNOWN,
                "The transaction with the specified transactionRef is rolled back already"
            )
        }

        // A round id is optional on the wire; the transaction ref keeps single-shot rounds distinct.
        val roundId = params.gameRoundRef ?: params.transactionRef

        var balance: PlayerBalance? = null

        if (params.withdraw > 0) {
            balance = bus(
                PlaceSpinSessionCommand(
                    session = session,
                    gameSymbol = params.gameId,
                    externalRoundId = roundId,
                    externalSpinId = params.transactionRef.placeSpinId(),
                    amount = params.withdraw.toSystemUnits(session.currency),
                )
            )
        }

        if (params.deposit > 0) {
            balance = bus(
                SettleSpinSessionCommand(
                    session = session,
                    gameSymbol = params.gameId,
                    externalRoundId = roundId,
                    externalSpinId = params.transactionRef.settleSpinId(),
                    amount = params.deposit.toSystemUnits(session.currency),
                )
            )
        }

        // A zero/zero call is a legal round marker; answer with the balance as it stands.
        val newBalance = balance ?: bus(FindSessionBalanceQuery(session))

        if (params.reason == REASON_GAME_PLAY_FINAL) closeRound(session, roundId)

        return json.encodeToJsonElement(
            TransactionResult.serializer(),
            TransactionResult(
                newBalance = newBalance.toMinorUnits(session.currency),
                transactionId = params.transactionRef,
            )
        )
    }

    private suspend fun rollbackTransaction(params: RollbackTransactionParams): JsonElement {
        val session = resolveSession(params.sessionAlternativeId, currency = null)

        // Marked before anything is reversed, so a transaction still in flight — the provider may
        // send the rollback first — is refused when it arrives rather than executed and orphaned.
        guardPort.markRolledBack(
            key = "$AGGREGATOR_IDENTITY:${params.transactionRef}",
            ttlSeconds = ROLLBACK_TTL_SECONDS,
        )

        bus(
            RollbackSpinSessionCommand(
                session = session,
                // Win first, then bet: reclaiming before refunding keeps the balance non-negative.
                externalSpinIds = listOf(
                    params.transactionRef.settleSpinId(),
                    params.transactionRef.placeSpinId(),
                ),
            )
        )

        // The provider expects an empty result object, not a null result.
        return JsonObject(emptyMap())
    }

    /** The money has already moved by this point, so a round that cannot be closed must not fail the
     *  call — the provider would roll back a transaction that legitimately went through. */
    private suspend fun closeRound(session: Session, roundId: String) {
        runCatching { bus(EndRoundSessionCommand(session = session, externalRoundId = roundId)) }
            .onFailure { logger.warn("GamingFlow round {} not closed: {}", roundId, it.message) }
    }

    private suspend fun resolveSession(sessionAlternativeId: String?, currency: String?): Session {
        val token = sessionAlternativeId
            ?: throw SeamlessException(ERR_INTERNAL, "sessionAlternativeId is required")

        val session = bus(FindSessionQuery(token))

        // The session is currency-locked at creation; a mismatch means the provider is talking about
        // a wallet we do not own.
        if (currency != null && !session.currency.value.equals(currency, ignoreCase = true)) {
            throw SeamlessException(ERR_ILLEGAL_CURRENCY, "Currency $currency is not supported for this session")
        }

        return session
    }

    private fun isFresh(timestamp: Long): Boolean {
        val now = System.currentTimeMillis() / MILLIS_PER_SECOND
        return kotlin.math.abs(now - timestamp) <= TIMESTAMP_TTL_SECONDS
    }

    private fun <T> JsonObject.decode(serializer: DeserializationStrategy<T>): T =
        runCatching { json.decodeFromJsonElement(serializer, this) }
            .getOrElse { throw SeamlessException(INVALID_PARAMS, "Invalid params") }

    private fun String.placeSpinId(): String = "$this$PLACE_SUFFIX"

    private fun String.settleSpinId(): String = "$this$SETTLE_SUFFIX"

    /** Provider minor units → wallet system units (nano). */
    private suspend fun Long.toSystemUnits(currency: Currency): Amount =
        Amount(currencyPort.convertToUnits(this / MINOR_UNITS_PER_MAJOR, currency))

    private suspend fun PlayerBalance.toMinorUnits(currency: Currency): Long =
        (currencyPort.convertFromUnits(total.value, currency) * MINOR_UNITS_PER_MAJOR).roundToLong()

    private suspend fun loadConfig(): GamingFlowConfig? =
        bus(FindAggregatorQuery(Identity(AGGREGATOR_IDENTITY)))
            .map { GamingFlowConfig(it.config) }
            .orElse(null)

    private fun Throwable.toRpcError(method: String): GamingFlowRpcError = when (this) {
        is SeamlessException -> GamingFlowRpcError(code, message.orEmpty())
        is InsufficientBalanceException ->
            GamingFlowRpcError(ERR_NOT_ENOUGH_MONEY, "The player doesn't have enough money to do the bet action")
        is MaxPlaceSpinException ->
            GamingFlowRpcError(ERR_MAX_BET_LIMIT_EXCEEDED, "The bet exceeds the player's maximum bet limit")
        is SessionNotFoundException ->
            // ErrInternal, not ErrUnknown: no session means no money moved, so a rollback cycle
            // would chase a transaction that never existed.
            GamingFlowRpcError(ERR_INTERNAL, "Unknown session")
        else -> {
            logger.error("GamingFlow $method failed", this)
            GamingFlowRpcError(ERR_UNKNOWN, "The request cannot be handled at the moment")
        }
    }

    private suspend fun ApplicationCall.respondSigned(
        config: GamingFlowConfig,
        signer: GamingFlowSigner,
        nonce: String,
        timestamp: Long,
        payload: GamingFlowRpcResponse,
    ) {
        val body = json.encodeToString(GamingFlowRpcResponse.serializer(), payload)

        // Signed with the nonce and timestamp we received, which is what lets the provider tie the
        // answer to its own request.
        response.header(HEADER_NONCE, nonce)
        response.header(HEADER_TIMESTAMP, timestamp.toString())
        response.header(HEADER_SUBJECT, config.subject)
        response.header(HEADER_SIGNATURE, signer.sign(body, nonce, timestamp))

        respondText(text = body, contentType = ContentType.Application.Json)
    }

    private suspend fun ApplicationCall.reject(reason: String) {
        logger.warn("GamingFlow webhook rejected: {}", reason)
        respondText(text = reason, status = HttpStatusCode.Unauthorized)
    }

    private class SeamlessException(val code: Int, message: String) : RuntimeException(message)

    companion object {
        /** The aggregator row this webhook reads its signing key and casino id from. */
        const val AGGREGATOR_IDENTITY = "gamingflow"

        private const val METHOD_GET_BALANCE = "getBalance"

        private const val METHOD_WITHDRAW_AND_DEPOSIT = "withdrawAndDeposit"

        private const val METHOD_ROLLBACK = "rollbackTransaction"

        private const val REASON_GAME_PLAY_FINAL = "GAME_PLAY_FINAL"

        private const val PLACE_SUFFIX = ":place"

        private const val SETTLE_SUFFIX = ":settle"

        private const val HEADER_NONCE = "X-Nonce"

        private const val HEADER_SIGNATURE = "X-Signature"

        private const val HEADER_SUBJECT = "X-Subject"

        private const val HEADER_TIMESTAMP = "X-Timestamp"

        private const val MILLIS_PER_SECOND = 1_000L

        private const val MINOR_UNITS_PER_MAJOR = 100.0

        /** The provider's recommended nonce window. */
        private const val NONCE_TTL_SECONDS = 60L

        /** The provider retries a rollback for 24 hours, so the marker has to outlive that. */
        private const val ROLLBACK_TTL_SECONDS = 25L * 60 * 60

        private const val TIMESTAMP_TTL_SECONDS = 10L * 60

        // JSON-RPC reserved codes.
        private const val PARSE_ERROR = -32700

        private const val METHOD_NOT_FOUND = -32601

        private const val INVALID_PARAMS = -32602

        // Operator error codes defined by the provider.
        private const val ERR_NOT_ENOUGH_MONEY = 1

        private const val ERR_ILLEGAL_CURRENCY = 2

        private const val ERR_NEGATIVE_DEPOSIT = 3

        private const val ERR_NEGATIVE_WITHDRAWAL = 4

        private const val ERR_MAX_BET_LIMIT_EXCEEDED = 6

        private const val ERR_INTERNAL = 7

        /** The only code that makes the provider start a rollback cycle. */
        private const val ERR_UNKNOWN = 8
    }
}
