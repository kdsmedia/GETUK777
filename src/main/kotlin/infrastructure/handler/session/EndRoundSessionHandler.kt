package infrastructure.handler.session

import application.ICommandHandler
import application.command.session.EndRoundSessionCommand
import application.usecase.FinishRoundUsecase
import domain.exception.domainRequireNotNull
import domain.exception.notfound.RoundNotFoundException
import domain.repository.IRoundRepository
import domain.vo.ExternalRoundId

class EndRoundSessionHandler(
    private val roundRepository: IRoundRepository,
    private val finishRoundUsecase: FinishRoundUsecase,
) : ICommandHandler<EndRoundSessionCommand, Unit> {

    override suspend fun handle(command: EndRoundSessionCommand): Result<Unit> = runCatching {
        val round = domainRequireNotNull(
            roundRepository.findByExternalIdAndSessionId(
                externalId = ExternalRoundId(command.externalRoundId),
                sessionId = command.session.id,
            )
        ) { RoundNotFoundException() }

        finishRoundUsecase.invoke(round).getOrThrow()
    }
}
