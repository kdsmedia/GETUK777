package infrastructure.aggregator.pragmatic.webhook

import application.Bus
import application.command.session.EndCasinoRoundSessionCommand
import application.port.external.ICurrencyPort
import application.query.session.FindCasinoSessionBalanceQuery
import application.query.session.FindCasinoSessionQuery
import application.command.session.PlaceSpinCasinoSessionCommand
import application.command.session.SettleSpinCasinoSessionCommand
import domain.exception.forbidden.InsufficientBalanceException
import domain.exception.forbidden.MaxPlaceSpinException
import domain.exception.notfound.CasinoSessionNotFoundException
import domain.model.PlayerBalance
import domain.model.CasinoSession
import domain.vo.Amount
import domain.vo.Currency
import infrastructure.aggregator.pragmatic.webhook.dto.PragmaticBetDto
import infrastructure.aggregator.pragmatic.webhook.dto.PragmaticResponse
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import java.math.BigDecimal

class PragmaticWebhook(
    private val bus: Bus,
    private val currencyPort: ICurrencyPort,
) {

    fun Route.route() = route("/pragmatic") {
        get("/authenticate.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            call.respond(authenticate(sessionToken))
        }

        get("/balance.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            call.respond(balance(sessionToken))
        }

        get("/bet.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            val payload = PragmaticBetDto(
                reference = call.parameters["reference"] ?: "",
                gameId = call.parameters["gameId"] ?: "",
                roundId = call.parameters["roundId"] ?: "",
                bonusCode = call.parameters["bonusCode"],
                amount = call.parameters["amount"] ?: "0"
            )

            call.respond(bet(sessionToken, payload))
        }

        get("/result.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            val payload = PragmaticBetDto(
                reference = call.parameters["reference"] ?: "",
                gameId = call.parameters["gameId"] ?: "",
                roundId = call.parameters["roundId"] ?: "",
                bonusCode = call.parameters["bonusCode"],
                amount = call.parameters["amount"] ?: "0",
                promoWinAmount = call.parameters["promoWinAmount"] ?: "0"
            )

            call.respond(result(sessionToken, payload))
        }

        get("/bonusWin.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            call.respond(balance(sessionToken))
        }

        get("/jackpotWin.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            call.respond(balance(sessionToken))
        }

        get("/refund.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            call.respond(refund(sessionToken))
        }

        get("/endRound.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            val roundId = call.parameters["roundId"] ?: ""

            call.respond(endRound(sessionToken, roundId))
        }

        get("/adjustment.html") {
            val sessionToken = call.parameters["token"]
                ?: return@get call.respond(PragmaticResponse.Error.SESSION_EXPIRED)

            val roundId = call.parameters["roundId"] ?: ""
            val reference = call.parameters["reference"] ?: ""
            val amount = call.parameters["amount"] ?: "0"
            val gameId = call.parameters["gameId"] ?: ""

            call.respond(adjustment(sessionToken, roundId, reference, amount, gameId))
        }
    }

    private suspend fun authenticate(sessionToken: String): PragmaticResponse {
        return runCatching {
            val session = bus(FindCasinoSessionQuery(sessionToken))
            bus(FindCasinoSessionBalanceQuery(session))
        }.toBalanceResponse(userId = sessionToken)
    }

    private suspend fun balance(sessionToken: String): PragmaticResponse {
        return runCatching {
            val session = bus(FindCasinoSessionQuery(sessionToken))
            bus(FindCasinoSessionBalanceQuery(session))
        }.toBalanceResponse()
    }

    private suspend fun bet(sessionToken: String, payload: PragmaticBetDto): PragmaticResponse {
        return runCatching {
            val session = bus(FindCasinoSessionQuery(sessionToken))
            val amount = session.toSystemUnit(BigDecimal(payload.amount))
            bus(PlaceSpinCasinoSessionCommand(
                session = session,
                gameSymbol = payload.gameId,
                externalRoundId = payload.roundId,
                externalSpinId = payload.reference,
                freespinId = payload.bonusCode,
                amount = amount
            ))
        }.toBalanceResponse(transactionId = payload.reference)
    }

    private suspend fun result(sessionToken: String, payload: PragmaticBetDto): PragmaticResponse {
        val totalAmount = BigDecimal(payload.amount).add(BigDecimal(payload.promoWinAmount))

        val result = runCatching {
            val session = bus(FindCasinoSessionQuery(sessionToken))
            val amount = session.toSystemUnit(totalAmount)
            bus(SettleSpinCasinoSessionCommand(
                session = session,
                gameSymbol = payload.gameId,
                externalRoundId = payload.roundId,
                externalSpinId = payload.reference,
                freespinId = payload.bonusCode,
                amount = amount
            ))
        }

        return result.toBalanceResponse(transactionId = payload.reference)
    }

    private suspend fun endRound(sessionToken: String, roundId: String): PragmaticResponse {
        runCatching {
            val session = bus(FindCasinoSessionQuery(sessionToken))
            bus(EndCasinoRoundSessionCommand(
                session = session,
                externalRoundId = roundId
            ))
        }

        return balance(sessionToken)
    }

    private suspend fun refund(sessionToken: String): PragmaticResponse {
        // TODO: implement RollbackSpinCasinoSessionCommand for proper refund handling
        return balance(sessionToken)
    }

    private suspend fun adjustment(
        sessionToken: String,
        roundId: String,
        reference: String,
        amount: String,
        gameId: String
    ): PragmaticResponse {
        val decimalAmount = BigDecimal(amount)
        val isDebit = decimalAmount < BigDecimal.ZERO

        return if (isDebit) {
            runCatching {
                val session = bus(FindCasinoSessionQuery(sessionToken))
                val converted = session.toSystemUnit(decimalAmount.abs())
                bus(PlaceSpinCasinoSessionCommand(
                    session = session,
                    gameSymbol = gameId,
                    externalRoundId = roundId,
                    externalSpinId = reference,
                    amount = converted
                ))
            }.toBalanceResponse()
        } else {
            runCatching {
                val session = bus(FindCasinoSessionQuery(sessionToken))
                val converted = session.toSystemUnit(decimalAmount)
                bus(SettleSpinCasinoSessionCommand(
                    session = session,
                    gameSymbol = gameId,
                    externalRoundId = roundId,
                    externalSpinId = reference,
                    amount = converted
                ))
            }.toBalanceResponse()
        }
    }

    /** CasinoProvider decimal amount + session currency → wallet system unit (nano). */
    private suspend fun CasinoSession.toSystemUnit(amount: BigDecimal): Amount =
        Amount(currencyPort.convertToUnits(amount.toDouble(), currency))

    /** Wallet system unit (nano) → provider decimal string, using the balance's currency. */
    private suspend fun systemUnitToProvider(amount: Amount, currency: Currency): String =
        BigDecimal.valueOf(currencyPort.convertFromUnits(amount.value, currency)).toPlainString()

    private suspend fun Result<PlayerBalance>.toBalanceResponse(
        transactionId: String? = null,
        userId: String? = null
    ): PragmaticResponse {
        return fold(
            onSuccess = { balance ->
                PragmaticResponse.Success(
                    cash = systemUnitToProvider(balance.realAmount, balance.currency),
                    bonus = systemUnitToProvider(balance.bonusAmount, balance.currency),
                    currency = balance.currency.value,
                    userId = userId,
                    transactionId = transactionId
                )
            },
            onFailure = { exception ->
                when (exception) {
                    is CasinoSessionNotFoundException -> PragmaticResponse.Error.SESSION_EXPIRED
                    is InsufficientBalanceException -> PragmaticResponse.Error.INSUFFICIENT_FUNDS
                    is MaxPlaceSpinException -> PragmaticResponse.Error.BET_LIMIT_EXCEEDED
                    else -> PragmaticResponse.Error.UNEXPECTED_ERROR
                }
            }
        )
    }
}
