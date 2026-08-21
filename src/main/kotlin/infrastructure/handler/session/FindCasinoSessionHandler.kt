package infrastructure.handler.session

import application.IQueryHandler
import application.query.session.FindCasinoSessionQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoSessionNotFoundException
import domain.model.CasinoSession
import domain.repository.ICasinoSessionRepository

class FindCasinoSessionHandler(
    private val sessionRepository: ICasinoSessionRepository,
) : IQueryHandler<FindCasinoSessionQuery, CasinoSession> {

    override suspend fun handle(query: FindCasinoSessionQuery): CasinoSession =
        domainRequireNotNull(sessionRepository.findByToken(query.token)) { CasinoSessionNotFoundException() }
}
