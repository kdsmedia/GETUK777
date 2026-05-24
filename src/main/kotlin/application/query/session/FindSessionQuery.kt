package application.query.session

import application.IQuery
import domain.model.Session

data class FindSessionQuery(val token: String) : IQuery<Session>