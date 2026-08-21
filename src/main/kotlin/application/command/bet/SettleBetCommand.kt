package application.command.bet

import application.ICommand
import domain.model.Bet
import domain.vo.Amount
import domain.vo.Currency

data class SettleBetCommand(
    val externalId: String,
    val transactionId: String,
    val currency: Currency,
    val realAmount: Amount,
    val bonusAmount: Amount,
    val credit: Boolean,
    val won: Boolean,
) : ICommand<SettleBetResult>

/** [debt] is the part of a clawback the balance could not cover — reported back to the aggregator. */
data class SettleBetResult(
    val bet: Bet,
    val debt: Amount,
)
