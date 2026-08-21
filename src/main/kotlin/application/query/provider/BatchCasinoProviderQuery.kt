package application.query.provider

import application.IQuery
import domain.model.CasinoProvider
import domain.vo.Identity

class BatchCasinoProviderQuery(
    val identities: List<Identity>,
) : IQuery<List<CasinoProvider>>
