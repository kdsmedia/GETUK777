package application.command.wheel

import application.ICommand
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId

/** Fortune Wheel win: credits the player's balance when the spin outcome is a win. */
data class PayoutWheelCommand(
    val playerId: PlayerId,
    val transactionId: String,
    val currency: Currency,
    val amount: Amount,
) : ICommand<Unit>
