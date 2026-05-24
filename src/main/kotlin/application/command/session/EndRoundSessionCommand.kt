package application.command.session

import application.ICommand
import domain.model.Session

data class EndRoundSessionCommand(
    val session: Session,

    val externalRoundId: String,
) : ICommand<Unit>
