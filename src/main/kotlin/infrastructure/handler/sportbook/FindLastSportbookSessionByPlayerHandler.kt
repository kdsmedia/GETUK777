package infrastructure.handler.sportbook

import application.IQueryHandler
import application.query.sportbook.FindLastSportbookSessionByPlayerQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.SportbookSessionNotFoundException
import domain.model.SportbookSession
import domain.repository.ISportbookSessionRepository
import domain.vo.PlayerId

class FindLastSportbookSessionByPlayerHandler(
    private val sessionRepository: ISportbookSessionRepository,
) : IQueryHandler<FindLastSportbookSessionByPlayerQuery, SportbookSession> {

    override suspend fun handle(query: FindLastSportbookSessionByPlayerQuery): SportbookSession =
        domainRequireNotNull(
            sessionRepository.findLastByPlayerId(PlayerId(query.playerId))
        ) { SportbookSessionNotFoundException() }
}
