package infrastructure.handler.session

import application.ICommandHandler
import application.command.session.PlaceSpinSessionCommand
import application.usecase.ProcessSpinUsecase
import domain.model.PlayerBalance
import domain.repository.IRoundRepository
import domain.service.SpinFactory
import domain.vo.ExternalRoundId
import domain.vo.ExternalSpinId
import domain.vo.FreespinId

class PlaceSpinSessionHandler(
    private val roundRepository: IRoundRepository,
    private val processSpinUsecase: ProcessSpinUsecase,
) : ICommandHandler<PlaceSpinSessionCommand, PlayerBalance> {

    override suspend fun handle(command: PlaceSpinSessionCommand): Result<PlayerBalance> = runCatching {
        val session = command.session

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

        processSpinUsecase.invoke(spin).getOrThrow().balance
    }
}
