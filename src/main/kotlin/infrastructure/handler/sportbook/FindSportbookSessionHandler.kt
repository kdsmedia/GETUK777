package infrastructure.handler.sportbook

import application.IQueryHandler
import application.query.sportbook.FindSportbookSessionQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.SportbookSessionNotFoundException
import domain.model.SportbookSession
import domain.repository.ISportbookSessionRepository
import domain.vo.SportbookSessionToken

class FindSportbookSessionHandler(
    private val sessionRepository: ISportbookSessionRepository,
) : IQueryHandler<FindSportbookSessionQuery, SportbookSession> {

    override suspend fun handle(query: FindSportbookSessionQuery): SportbookSession =
        domainRequireNotNull(
            sessionRepository.findByToken(SportbookSessionToken(query.token))
        ) { SportbookSessionNotFoundException() }
}
