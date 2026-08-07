package domain.service

import domain.exception.conflict.RoundAlreadyFinishedException
import domain.exception.domainRequire
import domain.model.Round
import domain.model.Spin
import domain.model.SpinType
import domain.vo.Amount
import domain.vo.ExternalSpinId

object SpinFactory {

    fun place(round: Round, externalId: ExternalSpinId, amount: Amount): Spin {
        domainRequire(!round.isFinished) { RoundAlreadyFinishedException() }
        return Spin(
            externalId = externalId,
            round = round,
            type = SpinType.PLACE,
            amount = amount,
        )
    }

    fun settle(round: Round, externalId: ExternalSpinId, amount: Amount): Spin {
        domainRequire(!round.isFinished) { RoundAlreadyFinishedException() }
        return Spin(
            externalId = externalId,
            round = round,
            type = SpinType.SETTLE,
            amount = amount,
        )
    }

    /**
     * A rollback always undoes a specific spin, so [reference] is required rather than optional —
     * [SpinBalanceCalculator] reads the original real/bonus split off it and cannot work without it.
     * A finished round is no obstacle: a provider may give up on a transaction long after the round
     * closed, and the money still has to move back.
     */
    fun rollback(round: Round, externalId: ExternalSpinId, reference: Spin): Spin =
        Spin(
            externalId = externalId,
            round = round,
            reference = reference,
            type = SpinType.ROLLBACK,
            amount = reference.amount,
        )
}
