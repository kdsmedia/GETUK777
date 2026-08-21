package infrastructure.handler.sportbook

import application.ICommandHandler
import application.command.sportbook.ExchangeSportbookTokenCommand
import domain.repository.ISportbookSessionRepository
import java.util.UUID

class ExchangeSportbookTokenHandler(
    private val sessionRepository: ISportbookSessionRepository,
) : ICommandHandler<ExchangeSportbookTokenCommand, String> {

    override suspend fun handle(command: ExchangeSportbookTokenCommand): Result<String> = runCatching {
        val privateToken = UUID.randomUUID().toString()

        sessionRepository.save(command.session.copy(externalToken = privateToken))

        privateToken
    }
}
