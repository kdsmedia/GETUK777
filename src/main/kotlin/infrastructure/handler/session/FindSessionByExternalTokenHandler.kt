package infrastructure.handler.session

import application.IQueryHandler
import application.query.session.FindSessionByExternalTokenQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.SessionNotFoundException
import domain.model.Session
import domain.repository.ISessionRepository

class FindSessionByExternalTokenHandler(
    private val sessionRepository: ISessionRepository,
) : IQueryHandler<FindSessionByExternalTokenQuery, Session> {

    override suspend fun handle(query: FindSessionByExternalTokenQuery): Session =
        domainRequireNotNull(
            sessionRepository.findByExternalToken(query.externalToken)
        ) { SessionNotFoundException() }
}
