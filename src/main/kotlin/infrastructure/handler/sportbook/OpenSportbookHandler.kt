package infrastructure.handler.sportbook

import application.ICommandHandler
import application.command.sportbook.OpenSportbookCommand
import application.usecase.OpenSportbookUsecase
import domain.model.SportbookSession

class OpenSportbookHandler(
    private val openSportbookUsecase: OpenSportbookUsecase,
) : ICommandHandler<OpenSportbookCommand, SportbookSession> {

    override suspend fun handle(command: OpenSportbookCommand): Result<SportbookSession> =
        openSportbookUsecase(playerId = command.playerId, currency = command.currency)
}
