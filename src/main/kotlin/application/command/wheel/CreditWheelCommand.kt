package application.command.wheel

import application.ICommand
import domain.model.SportbookSession
import domain.vo.Amount
import domain.vo.Currency

/** Fortune Wheel stake: debits the player's balance when the wheel is spun. */
data class CreditWheelCommand(
    val session: SportbookSession,
    val transactionId: String,
    val currency: Currency,
    val amount: Amount,
) : ICommand<Unit>
