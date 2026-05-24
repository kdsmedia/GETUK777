package infrastructure.handler.session

import application.IQueryHandler
import application.query.session.FindSessionQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.SessionNotFoundException
import domain.model.Session
import domain.repository.ISessionRepository

class FindSessionHandler(
    private val sessionRepository: ISessionRepository,
) : IQueryHandler<FindSessionQuery, Session> {

    override suspend fun handle(query: FindSessionQuery): Session =
        domainRequireNotNull(sessionRepository.findByToken(query.token)) { SessionNotFoundException() }
}
