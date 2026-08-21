package application.query.game

import application.IQuery
import domain.vo.Page
import domain.vo.Pageable

data class FindAllCasinoGameQuery(
    val filter: CasinoGameFilter,

    val pageable: Pageable,
) : IQuery<Page<CasinoGameView>>
