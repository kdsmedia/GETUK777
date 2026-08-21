package domain.model

import domain.exception.conflict.CasinoRoundAlreadyFinishedException
import domain.exception.domainRequire
import domain.util.ext.InstantExt
import domain.vo.ExternalCasinoRoundId
import domain.vo.FreespinId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CasinoRound(
    val id: Long = Long.MIN_VALUE,

    val externalId: ExternalCasinoRoundId,

    val freespinId: FreespinId? = null,

    val session: CasinoSession,

    val gameVariant: CasinoGameVariant = session.gameVariant,

    val createdAt: Instant = InstantExt.now(),

    val finishedAt: Instant? = null,
) {
    val isFinished: Boolean
        get() = finishedAt != null

    /**
     * Closes the round and returns the finished [CasinoRound]. The usecase publishes a
     * `CasinoRoundEvent` snapshot after persistence commits.
     *
     * Throws [CasinoRoundAlreadyFinishedException] if the round was already closed.
     */
    fun finish(): CasinoRound {
        domainRequire(!isFinished) { CasinoRoundAlreadyFinishedException() }
        return copy(finishedAt = InstantExt.now())
    }
}
