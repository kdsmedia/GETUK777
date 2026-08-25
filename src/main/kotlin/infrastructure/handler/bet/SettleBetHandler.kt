package infrastructure.handler.bet

import application.ICommandHandler
import application.command.bet.SettleBetCommand
import application.command.bet.SettleBetResult
import application.usecase.ProcessBetUsecase

class SettleBetHandler(
    private val processBetUsecase: ProcessBetUsecase,
) : ICommandHandler<SettleBetCommand, SettleBetResult> {

    override suspend fun handle(command: SettleBetCommand): Result<SettleBetResult> =
        processBetUsecase.settle(
            externalId = command.externalId,
            transactionId = command.transactionId,
            currency = command.currency,
            amount = command.amount,
            credit = command.credit,
            won = command.won,
        )
}
