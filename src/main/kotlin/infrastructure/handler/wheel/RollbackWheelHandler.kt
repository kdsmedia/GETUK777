package infrastructure.handler.wheel

import application.ICommandHandler
import application.command.wheel.RollbackWheelCommand
import application.usecase.ProcessWheelUsecase

class RollbackWheelHandler(
    private val processWheelUsecase: ProcessWheelUsecase,
) : ICommandHandler<RollbackWheelCommand, Unit> {

    override suspend fun handle(command: RollbackWheelCommand): Result<Unit> =
        processWheelUsecase.rollback(
            playerId = command.playerId,
            transactionId = command.transactionId,
            currency = command.currency,
            amount = command.amount,
        )
}
