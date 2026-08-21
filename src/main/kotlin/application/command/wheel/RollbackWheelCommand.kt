package application.command.wheel

import application.ICommand
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId

/** Cancels a previously issued Fortune Wheel `credit` (stake) transaction. */
data class RollbackWheelCommand(
    val playerId: PlayerId,
    val transactionId: String,
    val currency: Currency,
    val amount: Amount,
) : ICommand<Unit>
