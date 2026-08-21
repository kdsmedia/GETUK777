package application.query.sportbook

import application.IQuery
import domain.model.SportbookSession

data class FindLastSportbookSessionByPlayerQuery(val playerId: String) : IQuery<SportbookSession>
