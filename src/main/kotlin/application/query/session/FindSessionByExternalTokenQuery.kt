package application.query.session

import application.IQuery
import domain.model.Session

/**
 * Resolves a session by the identifier the PROVIDER minted for it, stored as
 * `Session.externalToken`. The counterpart of [FindSessionQuery], which looks up the token we
 * minted ourselves.
 */
data class FindSessionByExternalTokenQuery(val externalToken: String) : IQuery<Session>
