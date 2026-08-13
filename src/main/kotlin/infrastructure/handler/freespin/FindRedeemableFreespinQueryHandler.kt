package infrastructure.handler.freespin

import application.IQueryHandler
import application.query.freespin.FindRedeemableFreespinQuery
import domain.model.Freespin
import domain.repository.IFreespinRepository
import domain.util.ext.InstantExt

class FindRedeemableFreespinQueryHandler(
    private val freespinRepository: IFreespinRepository,
) : IQueryHandler<FindRedeemableFreespinQuery, Freespin?> {

    override suspend fun handle(query: FindRedeemableFreespinQuery): Freespin? =
        freespinRepository.findRedeemable(
            playerId = query.playerId,
            gameVariantId = query.gameVariantId,
            now = InstantExt.now(),
        )
}
