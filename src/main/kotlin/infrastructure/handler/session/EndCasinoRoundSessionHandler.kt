package infrastructure.handler.session

import application.ICommandHandler
import application.command.session.EndCasinoRoundSessionCommand
import application.usecase.FinishCasinoRoundUsecase
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoRoundNotFoundException
import domain.repository.ICasinoRoundRepository
import domain.vo.ExternalCasinoRoundId

class EndCasinoRoundSessionHandler(
    private val roundRepository: ICasinoRoundRepository,
    private val finishRoundUsecase: FinishCasinoRoundUsecase,
) : ICommandHandler<EndCasinoRoundSessionCommand, Unit> {

    override suspend fun handle(command: EndCasinoRoundSessionCommand): Result<Unit> = runCatching {
        val round = domainRequireNotNull(
            roundRepository.findByExternalIdAndSessionId(
                externalId = ExternalCasinoRoundId(command.externalRoundId),
                sessionId = command.session.id,
            )
        ) { CasinoRoundNotFoundException() }

        finishRoundUsecase.invoke(round).getOrThrow()
    }
}
