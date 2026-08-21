package infrastructure.handler.bet

import application.ICommandHandler
import application.command.bet.PlaceBetCommand
import application.usecase.ProcessBetUsecase
import domain.model.Bet

class PlaceBetHandler(
    private val processBetUsecase: ProcessBetUsecase,
) : ICommandHandler<PlaceBetCommand, Bet> {

    override suspend fun handle(command: PlaceBetCommand): Result<Bet> =
        processBetUsecase.place(
            session = command.session,
            transactionId = command.transactionId,
            currency = command.currency,
            amount = command.amount,
        )
}
