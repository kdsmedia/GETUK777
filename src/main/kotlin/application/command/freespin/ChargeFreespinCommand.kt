package application.command.freespin

import application.ICommand
import domain.model.Freespin

/** Spends free rounds off a grant. Returns the grant as it stands afterwards, because the provider
 *  expects the remaining count back in the same response. */
data class ChargeFreespinCommand(
    val referenceId: String,

    val count: Int,
) : ICommand<Freespin>
