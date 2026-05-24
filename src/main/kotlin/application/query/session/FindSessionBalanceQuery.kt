package application.query.session

import application.IQuery
import domain.model.PlayerBalance
import domain.model.Session

data class FindSessionBalanceQuery(val session: Session) : IQuery<PlayerBalance>