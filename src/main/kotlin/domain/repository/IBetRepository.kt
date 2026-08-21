package domain.repository

import domain.model.Bet
import domain.vo.ExternalBetId

interface IBetRepository {

    suspend fun save(bet: Bet): Bet

    suspend fun findById(id: Long): Bet?

    suspend fun findByExternalId(externalId: ExternalBetId): Bet?

    suspend fun deleteById(id: Long)
}
