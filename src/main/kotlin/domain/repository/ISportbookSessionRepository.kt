package domain.repository

import domain.model.SportbookSession
import domain.vo.PlayerId
import domain.vo.SportbookSessionToken

interface ISportbookSessionRepository {

    suspend fun save(session: SportbookSession): SportbookSession

    suspend fun findById(id: Long): SportbookSession?

    suspend fun findByToken(token: SportbookSessionToken): SportbookSession?

    suspend fun findByExternalToken(externalToken: String): SportbookSession?

    suspend fun findLastByPlayerId(playerId: PlayerId): SportbookSession?
}
