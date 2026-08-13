package domain.repository

import domain.model.Freespin
import domain.vo.FreespinId
import domain.vo.PlayerId
import kotlinx.datetime.Instant

interface IFreespinRepository {

    suspend fun save(freespin: Freespin): Freespin

    suspend fun findByReferenceId(referenceId: FreespinId): Freespin?

    /** The grant a spin on this game right now would draw from, or null if there is none. */
    suspend fun findRedeemable(playerId: PlayerId, gameVariantId: Long, now: Instant): Freespin?
}
