package infrastructure.handler.session

import application.IQueryHandler
import application.query.session.FindCasinoSessionByExternalTokenQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoSessionNotFoundException
import domain.model.CasinoSession
import domain.repository.ICasinoSessionRepository

class FindCasinoSessionByExternalTokenHandler(
    private val sessionRepository: ICasinoSessionRepository,
) : IQueryHandler<FindCasinoSessionByExternalTokenQuery, CasinoSession> {

    override suspend fun handle(query: FindCasinoSessionByExternalTokenQuery): CasinoSession =
        domainRequireNotNull(
            sessionRepository.findByExternalToken(query.externalToken)
        ) { CasinoSessionNotFoundException() }
}
