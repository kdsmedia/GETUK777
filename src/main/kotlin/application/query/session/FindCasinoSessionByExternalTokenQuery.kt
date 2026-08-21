package application.query.session

import application.IQuery
import domain.model.CasinoSession

/**
 * Resolves a session by the identifier the PROVIDER minted for it, stored as
 * `CasinoSession.externalToken`. The counterpart of [FindCasinoSessionQuery], which looks up the token we
 * minted ourselves.
 */
data class FindCasinoSessionByExternalTokenQuery(val externalToken: String) : IQuery<CasinoSession>
