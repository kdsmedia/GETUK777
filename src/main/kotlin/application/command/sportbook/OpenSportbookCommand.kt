package application.command.sportbook

import application.ICommand
import domain.model.SportbookSession
import domain.vo.Currency
import domain.vo.PlayerId

data class OpenSportbookCommand(
    val playerId: PlayerId,
    val currency: Currency,
) : ICommand<SportbookSession>
