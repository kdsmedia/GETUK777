package application.query.provider

import application.IQuery
import domain.vo.Page
import domain.vo.Pageable

data class FindAllProviderTagQuery(
    val pageable: Pageable,
) : IQuery<Page<String>>
