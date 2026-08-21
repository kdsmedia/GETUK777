package domain.model

import domain.util.ext.InstantExt
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.ExternalBetId
import domain.vo.PlayerId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Sportsbook bet type.
 */
@Serializable
enum class BetType {
    SINGLE,
    COMBO,
    SYSTEM
}

/**
 * Sportsbook bet lifecycle status. A settled bet can be re-settled by the aggregator
 * (official result correction), so WON/LOST are not terminal.
 */
@Serializable
enum class BetStatus {
    OPEN,
    WON,
    LOST
}

@Serializable
data class Bet(
    val id: Long = Long.MIN_VALUE,

    val externalId: ExternalBetId,

    val playerId: PlayerId,

    val session: SportbookSession,

    val currency: Currency,

    val betAmount: Amount,

    val winAmount: Amount = Amount.ZERO,

    val type: BetType,

    val status: BetStatus = BetStatus.OPEN,

    val selections: List<BetSelection>,

    val createdAt: Instant = InstantExt.now(),

    val updatedAt: Instant = InstantExt.now(),
)
