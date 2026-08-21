package application.query.game

import application.IQuery
import domain.vo.Identity

class BatchCasinoGameQuery(
    val identities: List<Identity>,
) : IQuery<List<CasinoGameView>>
