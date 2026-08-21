package domain.repository

import domain.model.CasinoSession

interface ICasinoSessionRepository {

    suspend fun save(session: CasinoSession): CasinoSession

    suspend fun findById(id: Long): CasinoSession?

    suspend fun findByToken(token: String): CasinoSession?

    suspend fun findByExternalToken(externalToken: String): CasinoSession?

}
