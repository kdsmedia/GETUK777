package application.query.provider

import application.IQuery
import domain.model.CasinoProvider
import domain.vo.Identity
import java.util.Optional

data class FindCasinoProviderQuery(
    val identity: Identity,
) : IQuery<Optional<CasinoProvider>>
