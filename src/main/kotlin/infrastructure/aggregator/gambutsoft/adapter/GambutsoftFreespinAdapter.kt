package infrastructure.aggregator.gambutsoft.adapter

import application.port.external.IFreespinPort
import domain.exception.conflict.FreespinNotSupportedException
import domain.vo.Currency
import domain.vo.PlayerId
import kotlinx.datetime.LocalDateTime

/**
 * The vendor has no freespin API at all — no grant, no query, no cancel. Free rounds exist only as
 * two optional fields on session creation, and the contract does not state the unit of the stake
 * they carry, so a grant cannot be honoured without guessing at money. The catalogue therefore
 * reports every game as `freeSpinEnable = false`, which refuses a grant before it reaches here.
 */
class GambutsoftFreespinAdapter : IFreespinPort {

    override suspend fun getPreset(gameSymbol: String): Map<String, Any> =
        throw FreespinNotSupportedException()

    override suspend fun create(
        presetValue: Map<String, Any>,
        referenceId: String,
        playerId: PlayerId,
        gameSymbol: String,
        currency: Currency,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        spinAmount: Long,
        spinCount: Int
    ): Unit = throw FreespinNotSupportedException()

    /** Nothing was ever created on the provider side, so there is nothing to revoke. */
    override suspend fun cancel(referenceId: String) = Unit
}
