package application.query.session

import application.IQuery
import domain.model.Session

data class FindSessionByPlayerIdQuery(val playerId: String) : IQuery<Session>
