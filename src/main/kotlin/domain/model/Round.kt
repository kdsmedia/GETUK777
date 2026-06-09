package domain.model

import domain.exception.conflict.RoundAlreadyFinishedException
import domain.exception.domainRequire
import domain.util.ext.LocalDateTimeExt
import domain.vo.ExternalRoundId
import domain.vo.FreespinId
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Round(
    val id: Long = Long.MIN_VALUE,

    val externalId: ExternalRoundId,

    val freespinId: FreespinId? = null,

    val session: Session,

    val gameVariant: GameVariant = session.gameVariant,

    val createdAt: LocalDateTime = LocalDateTimeExt.now(),

    val finishedAt: LocalDateTime? = null,
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
        return copy(finishedAt = LocalDateTimeExt.now())
    }
}
