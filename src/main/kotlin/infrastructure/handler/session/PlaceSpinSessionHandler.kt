package infrastructure.handler.session

import application.ICommandHandler
import application.command.session.PlaceSpinSessionCommand
import application.port.external.IWalletPort
import application.usecase.ProcessSpinUsecase
import domain.exception.conflict.SpinAlreadyExistsException
import domain.model.PlayerBalance
import domain.repository.IRoundRepository
import domain.repository.ISpinRepository
import domain.service.SpinFactory
import domain.vo.ExternalRoundId
import domain.vo.ExternalSpinId
import domain.vo.FreespinId

class PlaceSpinSessionHandler(
    private val roundRepository: IRoundRepository,
    private val spinRepository: ISpinRepository,
    private val processSpinUsecase: ProcessSpinUsecase,
    private val walletPort: IWalletPort,
) : ICommandHandler<PlaceSpinSessionCommand, PlayerBalance> {

    override suspend fun handle(command: PlaceSpinSessionCommand): Result<PlayerBalance> = runCatching {
        val session = command.session

        // Webhook replay guard: aggregators redeliver on timeout/retry, so this spin may already
        // be committed for this session. Answer success with the current balance — no second
        // spin row, no second wallet move, no duplicate event.
        val existingSpin = spinRepository.findByExternalId(command.externalSpinId)
        if (existingSpin != null && existingSpin.round.session.id == session.id) {
            return@runCatching walletPort.findBalance(playerId = session.playerId, currency = session.currency)
        }

        val externalRoundId = ExternalRoundId(command.externalRoundId)
        val freespinId = command.freespinId?.let { FreespinId(it) }

        val round = roundRepository.findByExternalIdAndSessionId(
            externalId = externalRoundId,
            sessionId = session.id,
        )?.copy(session = session) ?: roundRepository.save(
            session.openRound(externalId = externalRoundId, freespinId = freespinId),
        )

        val spin = SpinFactory.place(
            round = round,
            externalId = ExternalSpinId(command.externalSpinId),
            amount = command.amount,
        )

        // The lookup above cannot see a redelivery that is still in flight, so the unique
        // constraint on the external id is what actually decides the winner. The loser answers
        // exactly as the lookup would have: success, with the balance the winner left behind.
        try {
            processSpinUsecase.invoke(spin).getOrThrow().balance
        } catch (_: SpinAlreadyExistsException) {
            walletPort.findBalance(playerId = session.playerId, currency = session.currency)
        }
    }
}
