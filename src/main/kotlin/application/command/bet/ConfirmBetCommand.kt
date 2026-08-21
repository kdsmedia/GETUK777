package application.command.bet

import application.ICommand
import domain.model.Bet
import domain.model.BetSelection
import domain.model.BetType

data class ConfirmBetCommand(
    val transactionId: String,
    val externalId: String,
    val type: BetType,
    val selections: List<BetSelection>,
) : ICommand<Bet>
