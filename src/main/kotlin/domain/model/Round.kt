package domain.model

import domain.exception.conflict.RoundAlreadyFinishedException
import domain.exception.domainRequire
import domain.util.ext.InstantExt
import domain.vo.ExternalRoundId
import domain.vo.FreespinId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Round(
    val id: Long = Long.MIN_VALUE,

    val externalId: ExternalRoundId,

    val freespinId: FreespinId? = null,

    val session: Session,

    val gameVariant: GameVariant = session.gameVariant,

    val createdAt: Instant = InstantExt.now(),

    val finishedAt: Instant? = null,
) {
    val isFinished: Boolean
        get() = finishedAt != null

    /**
     * Closes the round and returns the finished [Round]. The usecase publishes a
     * `RoundEvent` snapshot after persistence commits.
     *
     * Throws [RoundAlreadyFinishedException] if the round was already closed.
     */
    fun finish(): Round {
        domainRequire(!isFinished) { RoundAlreadyFinishedException() }
        return copy(finishedAt = InstantExt.now())
    }
}
