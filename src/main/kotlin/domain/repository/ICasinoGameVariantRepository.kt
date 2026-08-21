package domain.repository

import domain.model.CasinoGameVariant
import domain.vo.Identity

interface ICasinoGameVariantRepository {

    suspend fun save(gameVariant: CasinoGameVariant): CasinoGameVariant

    suspend fun saveAll(gameVariants: List<CasinoGameVariant>): List<CasinoGameVariant>

    suspend fun findById(id: Long): CasinoGameVariant?

    suspend fun findBySymbol(symbol: String): CasinoGameVariant?

    suspend fun findAllByGame(gameIdentity: Identity): List<CasinoGameVariant>

    suspend fun findActiveByGameIdentity(gameIdentity: Identity): CasinoGameVariant?

    suspend fun findAllByIntegration(integration: String): List<CasinoGameVariant>

}
