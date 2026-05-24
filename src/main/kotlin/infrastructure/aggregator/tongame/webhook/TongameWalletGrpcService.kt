package infrastructure.aggregator.tongame.webhook

import application.Bus
import application.command.session.EndRoundSessionCommand
import application.command.session.PlaceSpinSessionCommand
import application.command.session.SettleSpinSessionCommand
import application.query.session.FindSessionBalanceQuery
import domain.model.PlayerBalance
import domain.vo.Amount
import slot.v1.dto.BalanceRequest
import slot.v1.dto.BalanceResponse
import slot.v1.dto.CloseRoundRequest
import slot.v1.dto.CreditRequest
import slot.v1.dto.DebitRequest
import slot.v1.dto.OpenRoundRequest
import slot.v1.service.WalletServiceGrpcKt

/**
 * Inbound TONGame wallet "webhook" over gRPC (slot.v1.WalletService). The provider dials
 * this server per round; callbacks carry our own session token, so every operation resolves
 * through the existing spin pipeline via [Bus]. Round opening is lazy (first [debit]).
 */
class TongameWalletGrpcService(
    private val bus: Bus,
) : WalletServiceGrpcKt.WalletServiceCoroutineImplBase() {

    override suspend fun balance(request: BalanceRequest): BalanceResponse =
        bus(FindSessionBalanceQuery(request.sessionToken)).toResponse()

    override suspend fun openRound(request: OpenRoundRequest): BalanceResponse =
        bus(FindSessionBalanceQuery(request.sessionToken)).toResponse()

    override suspend fun debit(request: DebitRequest): BalanceResponse =
        bus(
            PlaceSpinSessionCommand(
                sessionToken = request.sessionToken,
                externalRoundId = request.roundId,
                externalSpinId = "${request.roundId}:debit",
                amount = Amount(request.amount),
            )
        ).toResponse()

    override suspend fun credit(request: CreditRequest): BalanceResponse =
        bus(
            SettleSpinSessionCommand(
                sessionToken = request.sessionToken,
                externalRoundId = request.roundId,
                externalSpinId = "${request.roundId}:credit",
                amount = Amount(request.amount),
            )
        ).toResponse()

    override suspend fun closeRound(request: CloseRoundRequest): BalanceResponse {
        bus(EndRoundSessionCommand(sessionToken = request.sessionToken, externalRoundId = request.roundId))

        return bus(FindSessionBalanceQuery(request.sessionToken)).toResponse()
    }

    private fun PlayerBalance.toResponse(): BalanceResponse =
        BalanceResponse.newBuilder()
            .setAmount(total.value)
            .setCurrency(currency.value)
            .build()
}
