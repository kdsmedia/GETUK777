package application.command.bet

import application.ICommand
import domain.vo.Amount
import domain.vo.Currency

data class RollbackBetCommand(
    val transactionId: String,
    val currency: Currency,
    val amount: Amount,
) : ICommand<Unit>
