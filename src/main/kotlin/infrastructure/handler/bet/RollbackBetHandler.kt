package infrastructure.handler.bet

import application.ICommandHandler
import application.command.bet.RollbackBetCommand
import application.usecase.ProcessBetUsecase

class RollbackBetHandler(
    private val processBetUsecase: ProcessBetUsecase,
) : ICommandHandler<RollbackBetCommand, Unit> {

    override suspend fun handle(command: RollbackBetCommand): Result<Unit> =
        processBetUsecase.rollback(
            transactionId = command.transactionId,
            currency = command.currency,
            amount = command.amount,
        )
}
