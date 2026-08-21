package application.query.session

import application.IQuery
import domain.model.CasinoSession

data class FindCasinoSessionQuery(val token: String) : IQuery<CasinoSession>