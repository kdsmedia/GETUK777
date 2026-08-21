package application.command.session

import application.ICommand
import domain.model.CasinoSession

data class EndCasinoRoundSessionCommand(
    val session: CasinoSession,

    val externalRoundId: String,
) : ICommand<Unit>
