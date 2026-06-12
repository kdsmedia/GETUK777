package infrastructure.handler.session

import application.ICommandHandler
import application.command.session.SettleSpinSessionCommand
import application.port.external.IWalletPort
import application.usecase.ProcessSpinUsecase
import domain.exception.domainRequireNotNull
import domain.exception.notfound.RoundNotFoundException
import domain.model.PlayerBalance
import domain.repository.IRoundRepository
import domain.repository.ISpinRepository
import domain.service.SpinFactory
import domain.vo.ExternalRoundId
import domain.vo.ExternalSpinId

class SettleSpinSessionHandler(
    private val roundRepository: IRoundRepository,
    private val spinRepository: ISpinRepository,
    private val processSpinUsecase: ProcessSpinUsecase,
    private val walletPort: IWalletPort,
) : ICommandHandler<SettleSpinSessionCommand, PlayerBalance> {

    override suspend fun handle(command: SettleSpinSessionCommand): Result<PlayerBalance> = runCatching {
        val session = command.session

        // Webhook replay guard: aggregators redeliver on timeout/retry, so this spin may already
        // be committed for this session. Answer success with the current balance — no second
        // spin row, no second wallet move, no duplicate event.
        val existingSpin = spinRepository.findByExternalId(command.externalSpinId)
        if (existingSpin != null && existingSpin.round.session.id == session.id) {
            return@runCatching walletPort.findBalance(playerId = session.playerId, currency = session.currency)
        }

        val round = domainRequireNotNull(
            roundRepository.findByExternalIdAndSessionId(
                externalId = ExternalRoundId(command.externalRoundId),
                sessionId = session.id,
            )
        ) { RoundNotFoundException() }.copy(session = session)

        val spin = SpinFactory.settle(
            round = round,
            externalId = ExternalSpinId(command.externalSpinId),
            amount = command.amount,
        )

        processSpinUsecase.invoke(spin).getOrThrow().balance
    }
}
