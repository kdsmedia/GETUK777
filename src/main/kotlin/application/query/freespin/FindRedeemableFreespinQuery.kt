package application.query.freespin

import application.IQuery
import domain.model.Freespin
import domain.vo.PlayerId

/**
 * The grant a spin on this game would draw from right now, or null when the player has none.
 * Null is a normal answer — most spins are not free — so this does not throw.
 */
data class FindRedeemableFreespinQuery(
    val playerId: PlayerId,

    val gameVariantId: Long,
) : IQuery<Freespin?>
