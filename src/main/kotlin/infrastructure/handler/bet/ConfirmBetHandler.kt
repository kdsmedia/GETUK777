package infrastructure.handler.bet

import application.ICommandHandler
import application.command.bet.ConfirmBetCommand
import application.usecase.ProcessBetUsecase
import domain.model.Bet

class ConfirmBetHandler(
    private val processBetUsecase: ProcessBetUsecase,
) : ICommandHandler<ConfirmBetCommand, Bet> {

    override suspend fun handle(command: ConfirmBetCommand): Result<Bet> =
        processBetUsecase.confirm(
            transactionId = command.transactionId,
            externalId = command.externalId,
            type = command.type,
            selections = command.selections,
        )
}
