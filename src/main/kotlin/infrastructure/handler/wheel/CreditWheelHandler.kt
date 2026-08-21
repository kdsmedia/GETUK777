package infrastructure.handler.wheel

import application.ICommandHandler
import application.command.wheel.CreditWheelCommand
import application.usecase.ProcessWheelUsecase

class CreditWheelHandler(
    private val processWheelUsecase: ProcessWheelUsecase,
) : ICommandHandler<CreditWheelCommand, Unit> {

    override suspend fun handle(command: CreditWheelCommand): Result<Unit> =
        processWheelUsecase.credit(
            session = command.session,
            transactionId = command.transactionId,
            currency = command.currency,
            amount = command.amount,
        )
}
