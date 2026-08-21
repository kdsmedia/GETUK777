package infrastructure.handler.wheel

import application.ICommandHandler
import application.command.wheel.PayoutWheelCommand
import application.usecase.ProcessWheelUsecase

class PayoutWheelHandler(
    private val processWheelUsecase: ProcessWheelUsecase,
) : ICommandHandler<PayoutWheelCommand, Unit> {

    override suspend fun handle(command: PayoutWheelCommand): Result<Unit> =
        processWheelUsecase.payout(
            playerId = command.playerId,
            transactionId = command.transactionId,
            currency = command.currency,
            amount = command.amount,
        )
}
