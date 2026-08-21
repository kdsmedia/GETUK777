package application.command.bet

import application.ICommand
import domain.model.Bet
import domain.model.SportbookSession
import domain.vo.Amount
import domain.vo.Currency

data class PlaceBetCommand(
    val session: SportbookSession,
    val transactionId: String,
    val currency: Currency,
    val amount: Amount,
) : ICommand<Bet>
