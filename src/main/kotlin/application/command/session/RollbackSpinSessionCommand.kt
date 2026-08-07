package application.command.session

import application.ICommand
import domain.model.PlayerBalance
import domain.model.Session

/**
 * Reverses spins already committed for this session, in the given order.
 *
 * A provider transaction can have produced more than one spin — a bet and a win booked in one
 * call — and all of them have to come back. Order matters: reclaim the win before refunding the
 * bet, so the balance never has to dip below zero on the way.
 *
 * Ids that match nothing are skipped: a provider may ask to reverse a transaction that never
 * landed, and that is a no-op, not a failure.
 */
data class RollbackSpinSessionCommand(
    val session: Session,

    val externalSpinIds: List<String>,
) : ICommand<PlayerBalance>
