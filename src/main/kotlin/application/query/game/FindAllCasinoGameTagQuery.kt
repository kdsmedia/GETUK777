package application.query.game

import application.IQuery
import domain.vo.Page
import domain.vo.Pageable

data class FindAllCasinoGameTagQuery(
    val pageable: Pageable,
) : IQuery<Page<String>>
