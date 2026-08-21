package domain.repository

import domain.model.CasinoRound
import domain.vo.ExternalCasinoRoundId

interface ICasinoRoundRepository {

    suspend fun save(round: CasinoRound): CasinoRound

    suspend fun findById(id: Long): CasinoRound?

    suspend fun findByExternalIdAndSessionId(externalId: ExternalCasinoRoundId, sessionId: Long): CasinoRound?
}
