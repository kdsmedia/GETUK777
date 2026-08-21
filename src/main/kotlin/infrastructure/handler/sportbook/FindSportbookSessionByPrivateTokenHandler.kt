package infrastructure.handler.sportbook

import application.IQueryHandler
import application.query.sportbook.FindSportbookSessionByPrivateTokenQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.SportbookSessionNotFoundException
import domain.model.SportbookSession
import domain.repository.ISportbookSessionRepository

class FindSportbookSessionByPrivateTokenHandler(
    private val sessionRepository: ISportbookSessionRepository,
) : IQueryHandler<FindSportbookSessionByPrivateTokenQuery, SportbookSession> {

    override suspend fun handle(query: FindSportbookSessionByPrivateTokenQuery): SportbookSession =
        domainRequireNotNull(
            sessionRepository.findByExternalToken(query.privateToken)
        ) { SportbookSessionNotFoundException() }
}
