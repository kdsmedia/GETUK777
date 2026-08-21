package application.command.sportbook

import application.ICommand
import domain.model.SportbookSession

/** Exchanges the session's one-time public token for a freshly minted private token. */
data class ExchangeSportbookTokenCommand(val session: SportbookSession) : ICommand<String>
