package application.query.sportbook

import application.IQuery
import domain.model.SportbookSession

data class FindSportbookSessionByPrivateTokenQuery(val privateToken: String) : IQuery<SportbookSession>
