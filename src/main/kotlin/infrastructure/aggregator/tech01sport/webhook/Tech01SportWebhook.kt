package infrastructure.aggregator.tech01sport.webhook

import application.Bus
import application.command.bet.ConfirmBetCommand
import application.command.bet.PlaceBetCommand
import application.command.bet.RollbackBetCommand
import application.command.bet.SettleBetCommand
import application.command.sportbook.ExchangeSportbookTokenCommand
import application.command.wheel.CreditWheelCommand
import application.command.wheel.PayoutWheelCommand
import application.command.wheel.RollbackWheelCommand
import application.port.external.IWalletPort
import application.query.sportbook.FindActiveSportbookAggregatorQuery
import application.query.sportbook.FindLastSportbookSessionByPlayerQuery
import application.query.sportbook.FindSportbookSessionByPrivateTokenQuery
import application.query.sportbook.FindSportbookSessionQuery
import domain.exception.DomainException
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.notfound.BetNotFoundException
import domain.exception.notfound.NotFoundException
import domain.exception.notfound.TransactionNotFoundException
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId
import infrastructure.aggregator.tech01sport.Tech01SportConfig
import infrastructure.aggregator.tech01sport.Tech01SportMoney
import infrastructure.aggregator.tech01sport.webhook.Tech01SportBetMapper.toDomain
import infrastructure.aggregator.tech01sport.webhook.dto.BatchData
import infrastructure.aggregator.tech01sport.webhook.dto.BatchItemResult
import infrastructure.aggregator.tech01sport.webhook.dto.BatchResponse
import infrastructure.aggregator.tech01sport.webhook.dto.CreatePrivateTokenData
import infrastructure.aggregator.tech01sport.webhook.dto.CreatePrivateTokenRequest
import infrastructure.aggregator.tech01sport.webhook.dto.CreatePrivateTokenResponse
import infrastructure.aggregator.tech01sport.webhook.dto.CreditBetRequest
import infrastructure.aggregator.tech01sport.webhook.dto.CreditRequest
import infrastructure.aggregator.tech01sport.webhook.dto.DebitBetByBatchRequest
import infrastructure.aggregator.tech01sport.webhook.dto.DebitByBatchRequest
import infrastructure.aggregator.tech01sport.webhook.dto.GetUserData
import infrastructure.aggregator.tech01sport.webhook.dto.GetUserRequest
import infrastructure.aggregator.tech01sport.webhook.dto.GetUserResponse
import infrastructure.aggregator.tech01sport.webhook.dto.GetUserWalletDto
import infrastructure.aggregator.tech01sport.webhook.dto.PrepareCreditBetRequest
import infrastructure.aggregator.tech01sport.webhook.dto.RollbackBetByBatchRequest
import infrastructure.aggregator.tech01sport.webhook.dto.RollbackByBatchRequest
import infrastructure.aggregator.tech01sport.webhook.dto.SimpleResponse
import infrastructure.aggregator.tech01sport.webhook.dto.Tech01SportCode
import infrastructure.aggregator.tech01sport.webhook.dto.UserDataDto
import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Inbound 01.tech Sport webhooks (Betting System → partner). Every route answers HTTP 200 with
 * the status in the body — see [Tech01SportCode] — and verifies the `betting-signature` HMAC
 * via [Tech01SportSignatureVerifier] against the sportbook aggregator's `secretKeys`.
 *
 * Identity: `create-private-token` resolves the session by our one-time public token;
 * `prepare-credit-bet` by the minted private token; settlements and rollbacks resolve the bet
 * by its external id via the Bus commands, which delegate to `ProcessBetUsecase`.
 *
 * Fortune Wheel routes (`/credit`, `/debit-by-batch`, `/rollback-by-batch`) are pure wallet
 * passthrough via `ProcessWheelUsecase` — the wheel is deliberately outside the Bet model.
 */
class Tech01SportWebhook(
    private val bus: Bus,
    private val walletPort: IWalletPort,
) {

    private val logger = LoggerFactory.getLogger(Tech01SportWebhook::class.java)

    private val json = Json { ignoreUnknownKeys = true }

    // The 01.tech validator rejects explicit nulls (`"debt": null`) — optional fields must be
    // absent. The app-wide serializer writes explicit nulls, so responses use this one.
    private val responseJson = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    fun Route.route() = route("/tech01sport") {
        post("/ping") {
            val raw = call.receiveText()
            val config = activeConfig()

            if (!verified(call, raw, config)) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            call.respondJson(SimpleResponse(Tech01SportCode.SUCCESS, "Success"))
        }

        post("/create-private-token") {
            val raw = call.receiveText()

            val body = parse<CreatePrivateTokenRequest>(raw)
            if (body == null || body.publicToken.isBlank()) {
                call.respondJson(CreatePrivateTokenResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val session = runCatching { bus(FindSportbookSessionQuery(body.publicToken)) }.getOrNull()
            if (session == null) {
                call.respondJson(CreatePrivateTokenResponse(Tech01SportCode.WRONG_TOKEN, "Unknown public token"))
                return@post
            }

            val config = Tech01SportConfig(session.aggregator.config)

            if (!verified(call, raw, config)) {
                call.respondJson(CreatePrivateTokenResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            if (body.partnerId != config.partnerId) {
                call.respondJson(CreatePrivateTokenResponse(Tech01SportCode.WRONG_TOKEN, "Unknown partner"))
                return@post
            }

            // The public token is one-time: a second exchange attempt is rejected.
            if (session.externalToken != null) {
                call.respondJson(CreatePrivateTokenResponse(Tech01SportCode.WRONG_TOKEN, "Public token already used"))
                return@post
            }

            val privateToken = bus(ExchangeSportbookTokenCommand(session))

            call.respondJson(
                CreatePrivateTokenResponse(
                    code = Tech01SportCode.SUCCESS,
                    description = "Success",
                    data = CreatePrivateTokenData(
                        privateToken = privateToken,
                        userData = UserDataDto(id = session.playerId.value),
                    ),
                )
            )
        }

        post("/get-user") {
            val raw = call.receiveText()

            val body = parse<GetUserRequest>(raw)
            if (body == null) {
                call.respondJson(GetUserResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val config = activeConfig()

            if (!verified(call, raw, config)) {
                call.respondJson(GetUserResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            if (body.partnerId != config.partnerId) {
                call.respondJson(GetUserResponse(Tech01SportCode.WRONG_TOKEN, "Unknown partner"))
                return@post
            }

            // Currency comes from the player's last sportbook session; a player without one has
            // never opened the sportbook — answer the opt-in UserNotFound code.
            val session = runCatching { bus(FindLastSportbookSessionByPlayerQuery(body.userId)) }.getOrNull()
            if (session == null) {
                call.respondJson(GetUserResponse(Tech01SportCode.USER_NOT_FOUND, "Unknown user"))
                return@post
            }

            val balance = walletPort.findBalance(session.playerId, session.currency)

            call.respondJson(
                GetUserResponse(
                    code = Tech01SportCode.SUCCESS,
                    description = "Success",
                    data = GetUserData(
                        userData = UserDataDto(id = body.userId),
                        wallets = listOf(
                            GetUserWalletDto(
                                amount = Tech01SportMoney.fromAmount(balance.realAmount),
                                currencyCode = session.currency.value,
                                typeId = WALLET_TYPE_REAL,
                            ),
                            // Deliberately zero, not the player's real bonus balance. The
                            // sportbook spends real money only, so advertising a spendable bonus
                            // wallet invites a stake we would silently fund from real. The entry
                            // stays in the response because their contract expects both types.
                            GetUserWalletDto(
                                amount = Tech01SportMoney.fromAmount(Amount.ZERO),
                                currencyCode = session.currency.value,
                                typeId = WALLET_TYPE_BONUS,
                            ),
                        ),
                        activeCurrencyCode = session.currency.value,
                    ),
                )
            )
        }

        post("/prepare-credit-bet") {
            val raw = call.receiveText()

            val body = parse<PrepareCreditBetRequest>(raw)
            if (body == null || !Tech01SportMoney.isNegative(body.amount)) {
                call.respondJson(SimpleResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val session = runCatching { bus(FindSportbookSessionByPrivateTokenQuery(body.privateToken)) }.getOrNull()
            if (session == null) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_TOKEN, "Unknown private token"))
                return@post
            }

            val config = Tech01SportConfig(session.aggregator.config)

            if (!verified(call, raw, config)) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            if (body.partnerId != config.partnerId) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_TOKEN, "Unknown partner"))
                return@post
            }

            // A session works only in the currency it was opened with.
            if (body.currencyCode != session.currency.value) {
                call.respondJson(SimpleResponse(Tech01SportCode.VALIDATION_FAILED, "Currency mismatch"))
                return@post
            }

            try {
                bus(
                    PlaceBetCommand(
                        session = session,
                        transactionId = body.transactionId.toString(),
                        currency = Currency(body.currencyCode),
                        amount = Tech01SportMoney.toAmount(body.amount),
                    )
                )
                call.respondJson(SimpleResponse(Tech01SportCode.SUCCESS, "Success"))
            } catch (e: InsufficientBalanceException) {
                call.respondJson(SimpleResponse(Tech01SportCode.NOT_ENOUGH_BALANCE, "Not enough balance"))
            } catch (e: DomainException) {
                logger.warn("prepare-credit-bet failed: tx={} reason={}", body.transactionId, e.message)
                call.respondJson(SimpleResponse(Tech01SportCode.INTERNAL_ERROR, "Internal error"))
            }
        }

        post("/credit-bet") {
            val raw = call.receiveText()

            val body = parse<CreditBetRequest>(raw)
            if (body == null) {
                call.respondJson(SimpleResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val config = activeConfig()

            if (!verified(call, raw, config)) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            if (body.partnerId != config.partnerId) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_TOKEN, "Unknown partner"))
                return@post
            }

            try {
                bus(
                    ConfirmBetCommand(
                        transactionId = body.transactionId.toString(),
                        externalId = body.bet.id.toString(),
                        type = Tech01SportBetMapper.toBetType(body.bet.betType),
                        selections = body.bet.selections.map { it.toDomain() },
                    )
                )
                call.respondJson(SimpleResponse(Tech01SportCode.SUCCESS, "Success"))
            } catch (e: DomainException) {
                logger.warn("credit-bet failed: tx={} bet={} reason={}", body.transactionId, body.bet.id, e.message)
                call.respondJson(SimpleResponse(Tech01SportCode.INTERNAL_ERROR, "Internal error"))
            }
        }

        post("/debit-bet-by-batch") {
            val raw = call.receiveText()

            val body = parse<DebitBetByBatchRequest>(raw)
            if (body == null) {
                call.respondJson(BatchResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val config = activeConfig()

            if (!verified(call, raw, config)) {
                call.respondJson(BatchResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            val items = body.items.map { item ->
                try {
                    val result = bus(
                        SettleBetCommand(
                            externalId = item.bet.id.toString(),
                            transactionId = item.transactionId.toString(),
                            currency = Currency(item.currencyCode),
                            // Their `bonusAmount` is the accumulator-bonus SHARE OF THE WINNINGS,
                            // not a credit to the player's bonus wallet. The stake came off real,
                            // so the whole payout goes back to real — see ProcessBetUsecase.settle.
                            amount = Tech01SportMoney.toAmount(item.amount) +
                                (item.bonusAmount?.let { Tech01SportMoney.toAmount(it) } ?: Amount.ZERO),
                            credit = !Tech01SportMoney.isNegative(item.amount),
                            won = item.bet.status == Tech01SportBetMapper.STATUS_WIN,
                        )
                    )
                    BatchItemResult(
                        code = Tech01SportCode.SUCCESS,
                        transactionId = item.transactionId,
                        debt = result.debt.takeIf { it.value > 0 }?.let { Tech01SportMoney.fromAmount(it) },
                    )
                } catch (e: InsufficientBalanceException) {
                    BatchItemResult(code = Tech01SportCode.NOT_ENOUGH_BALANCE, transactionId = item.transactionId)
                } catch (e: Exception) {
                    logger.warn("debit-bet failed: tx={} bet={} reason={}", item.transactionId, item.bet.id, e.message)
                    BatchItemResult(code = Tech01SportCode.INTERNAL_ERROR, transactionId = item.transactionId)
                }
            }

            call.respondJson(BatchResponse(Tech01SportCode.SUCCESS, "Success", BatchData(items)))
        }

        post("/rollback-bet-by-batch") {
            val raw = call.receiveText()

            val body = parse<RollbackBetByBatchRequest>(raw)
            if (body == null) {
                call.respondJson(BatchResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val config = activeConfig()

            if (!verified(call, raw, config)) {
                call.respondJson(BatchResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            val items = body.items.map { item ->
                val code = try {
                    bus(
                        RollbackBetCommand(
                            transactionId = item.transactionId.toString(),
                            currency = Currency(item.currencyCode),
                            amount = Tech01SportMoney.toAmount(item.amount),
                        )
                    )
                    Tech01SportCode.SUCCESS
                } catch (e: BetNotFoundException) {
                    Tech01SportCode.NOT_FOUND_TRANSACTION
                } catch (e: NotFoundException) {
                    Tech01SportCode.NOT_FOUND_TRANSACTION
                } catch (e: Exception) {
                    logger.warn("rollback-bet failed: tx={} reason={}", item.transactionId, e.message)
                    Tech01SportCode.INTERNAL_ERROR
                }
                BatchItemResult(code = code, transactionId = item.transactionId)
            }

            call.respondJson(BatchResponse(Tech01SportCode.SUCCESS, "Success", BatchData(items)))
        }

        post("/credit") {
            val raw = call.receiveText()

            val body = parse<CreditRequest>(raw)
            if (body == null || !Tech01SportMoney.isNegative(body.amount)) {
                call.respondJson(SimpleResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val session = runCatching { bus(FindSportbookSessionByPrivateTokenQuery(body.privateToken)) }.getOrNull()
            if (session == null) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_TOKEN, "Unknown private token"))
                return@post
            }

            val config = Tech01SportConfig(session.aggregator.config)

            if (!verified(call, raw, config)) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            if (body.partnerId != config.partnerId) {
                call.respondJson(SimpleResponse(Tech01SportCode.WRONG_TOKEN, "Unknown partner"))
                return@post
            }

            // A session works only in the currency it was opened with.
            if (body.currencyCode != session.currency.value) {
                call.respondJson(SimpleResponse(Tech01SportCode.VALIDATION_FAILED, "Currency mismatch"))
                return@post
            }

            try {
                bus(
                    CreditWheelCommand(
                        session = session,
                        transactionId = body.transactionId.toString(),
                        currency = Currency(body.currencyCode),
                        amount = Tech01SportMoney.toAmount(body.amount),
                    )
                )
                call.respondJson(SimpleResponse(Tech01SportCode.SUCCESS, "Success"))
            } catch (e: InsufficientBalanceException) {
                call.respondJson(SimpleResponse(Tech01SportCode.NOT_ENOUGH_BALANCE, "Not enough balance"))
            } catch (e: DomainException) {
                logger.warn("wheel credit failed: tx={} reason={}", body.transactionId, e.message)
                call.respondJson(SimpleResponse(Tech01SportCode.INTERNAL_ERROR, "Internal error"))
            }
        }

        post("/debit-by-batch") {
            val raw = call.receiveText()

            val body = parse<DebitByBatchRequest>(raw)
            if (body == null) {
                call.respondJson(BatchResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val config = activeConfig()

            if (!verified(call, raw, config)) {
                call.respondJson(BatchResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            val items = body.items.map { item ->
                val code = try {
                    bus(
                        PayoutWheelCommand(
                            playerId = PlayerId(item.userId),
                            transactionId = item.transactionId.toString(),
                            currency = Currency(item.currencyCode),
                            amount = Tech01SportMoney.toAmount(item.amount),
                        )
                    )
                    Tech01SportCode.SUCCESS
                } catch (e: Exception) {
                    logger.warn("wheel payout failed: tx={} reason={}", item.transactionId, e.message)
                    Tech01SportCode.INTERNAL_ERROR
                }
                BatchItemResult(code = code, transactionId = item.transactionId)
            }

            call.respondJson(BatchResponse(Tech01SportCode.SUCCESS, "Success", BatchData(items)))
        }

        post("/rollback-by-batch") {
            val raw = call.receiveText()

            val body = parse<RollbackByBatchRequest>(raw)
            if (body == null) {
                call.respondJson(BatchResponse(Tech01SportCode.VALIDATION_FAILED, "Invalid request body"))
                return@post
            }

            val config = activeConfig()

            if (!verified(call, raw, config)) {
                call.respondJson(BatchResponse(Tech01SportCode.WRONG_SIGNATURE, "Wrong signature"))
                return@post
            }

            val items = body.items.map { item ->
                val code = try {
                    bus(
                        RollbackWheelCommand(
                            playerId = PlayerId(item.userId),
                            transactionId = item.transactionId.toString(),
                            currency = Currency(item.currencyCode),
                            amount = Tech01SportMoney.toAmount(item.amount),
                        )
                    )
                    Tech01SportCode.SUCCESS
                } catch (e: TransactionNotFoundException) {
                    Tech01SportCode.NOT_FOUND_TRANSACTION
                } catch (e: Exception) {
                    logger.warn("wheel rollback failed: tx={} reason={}", item.transactionId, e.message)
                    Tech01SportCode.INTERNAL_ERROR
                }
                BatchItemResult(code = code, transactionId = item.transactionId)
            }

            call.respondJson(BatchResponse(Tech01SportCode.SUCCESS, "Success", BatchData(items)))
        }
    }

    private suspend fun activeConfig(): Tech01SportConfig =
        Tech01SportConfig(bus(FindActiveSportbookAggregatorQuery).config)

    private fun verified(call: ApplicationCall, rawBody: String, config: Tech01SportConfig): Boolean =
        Tech01SportSignatureVerifier.verify(call.request.headers[SIGNATURE_HEADER], rawBody, config.secretKeys)

    private suspend inline fun <reified T> ApplicationCall.respondJson(body: T) =
        respondText(responseJson.encodeToString(body), ContentType.Application.Json)

    private inline fun <reified T> parse(raw: String): T? =
        runCatching { json.decodeFromString<T>(raw) }.getOrNull()

    private companion object {
        const val SIGNATURE_HEADER = "betting-signature"

        const val WALLET_TYPE_REAL = 1

        const val WALLET_TYPE_BONUS = 2
    }
}
