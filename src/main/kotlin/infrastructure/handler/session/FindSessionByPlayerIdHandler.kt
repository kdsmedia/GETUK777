package infrastructure.handler.session

import application.IQueryHandler
import application.query.session.FindSessionByPlayerIdQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.SessionNotFoundException
import domain.model.Session
import domain.repository.ISessionRepository

class FindSessionByPlayerIdHandler(
    private val sessionRepository: ISessionRepository,
) : IQueryHandler<FindSessionByPlayerIdQuery, Session> {

    override suspend fun handle(query: FindSessionByPlayerIdQuery): Session =
        domainRequireNotNull(sessionRepository.findByPlayerId(query.playerId)) { SessionNotFoundException() }
}
