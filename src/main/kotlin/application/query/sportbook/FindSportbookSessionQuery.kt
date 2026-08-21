package application.query.sportbook

import application.IQuery
import domain.model.SportbookSession

data class FindSportbookSessionQuery(val token: String) : IQuery<SportbookSession>
