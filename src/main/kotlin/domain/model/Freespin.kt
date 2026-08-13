package domain.model

import domain.exception.conflict.FreespinExhaustedException
import domain.exception.domainRequire
import domain.util.ext.InstantExt
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.FreespinId
import domain.vo.PlayerId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * A grant of free rounds on one game, and the operator's own count of how many are left.
 *
 * Aggregators split this two ways. Some own the counter and only tell us a round was free; others
 * (GamingFlow) keep nothing but the promotion id and expect the operator to hold the balance and
 * report it back on every call. This aggregate is that balance, so it is authoritative regardless
 * of which side the provider sits on.
 */
@Serializable
data class Freespin(
    val id: Long = Long.MIN_VALUE,

    /** The id shared with the provider — its `BonusId` / `freerounds_id`. */
    val referenceId: FreespinId,

    val playerId: PlayerId,

    val gameVariant: GameVariant,

    val currency: Currency,

    /** Stake per free round, in the wallet's system units. */
    val spinAmount: Amount,

    val totalCount: Int,

    val remainingCount: Int,

    val startAt: Instant,

    val endAt: Instant,

    val cancelledAt: Instant? = null,

    val createdAt: Instant = InstantExt.now(),
) {
    val isCancelled: Boolean
        get() = cancelledAt != null

    val isExhausted: Boolean
        get() = remainingCount <= 0

    fun isRedeemableAt(now: Instant): Boolean =
        !isCancelled && !isExhausted && now >= startAt && now < endAt

    /** Spends [count] rounds. Refuses rather than going negative — an over-charge is a provider bug
     *  we must not pay for. */
    fun charge(count: Int): Freespin {
        domainRequire(count in 1..remainingCount) { FreespinExhaustedException() }
        return copy(remainingCount = remainingCount - count)
    }

    /** Revoking is local: the remaining rounds are what makes the grant usable, so zeroing them is
     *  the cancellation, whatever the provider does or does not store. */
    fun cancel(): Freespin = copy(remainingCount = 0, cancelledAt = InstantExt.now())
}
