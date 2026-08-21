package application.query.session

import application.IQuery
import domain.model.PlayerBalance
import domain.model.CasinoSession

data class FindCasinoSessionBalanceQuery(val session: CasinoSession) : IQuery<PlayerBalance>