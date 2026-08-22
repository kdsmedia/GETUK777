package infrastructure.persistence.repository

import domain.repository.ICasinoGameVariantRepository
import domain.exception.domainRequireNotNull
import domain.exception.notfound.CasinoGameNotFoundException
import domain.model.CasinoGameVariant
import domain.vo.Identity
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.CasinoGameTable
import infrastructure.persistence.table.CasinoGameVariantTable
import infrastructure.persistence.table.CasinoProviderTable
import org.jetbrains.exposed.dao.load
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class CasinoGameVariantRepositoryImpl : ICasinoGameVariantRepository {

    private val variantChain = arrayOf(
        CasinoGameVariantEntity::game,
        CasinoGameEntity::provider,
        CasinoGameEntity::collections,
        CasinoProviderEntity::aggregator,
    )

    override suspend fun save(gameVariant: CasinoGameVariant): CasinoGameVariant = dbTransaction {
        if (gameVariant.id == Long.MIN_VALUE) {
            val id = CasinoGameVariantTable.insertAndGetId { it.fromDomain(gameVariant) }
            gameVariant.copy(id = id.value)
        } else {
            CasinoGameVariantTable.update({ CasinoGameVariantTable.id eq gameVariant.id }) { it.fromDomain(gameVariant) }
            gameVariant
        }
    }

    override suspend fun saveAll(gameVariants: List<CasinoGameVariant>): List<CasinoGameVariant> = dbTransaction {
        val gameIdentities = gameVariants.map { it.game.identity.value }.distinct()
        val gameIdMap = CasinoGameTable.select(CasinoGameTable.id, CasinoGameTable.identity)
            .where { CasinoGameTable.identity inList gameIdentities }
            .associate { it[CasinoGameTable.identity] to it[CasinoGameTable.id] }

        CasinoGameVariantTable.batchUpsert(gameVariants, keys = arrayOf(CasinoGameVariantTable.symbol)) { variant ->
            val gameId = domainRequireNotNull(gameIdMap[variant.game.identity.value]) {
                CasinoGameNotFoundException()
            }

            this[CasinoGameVariantTable.symbol] = variant.symbol.value
            this[CasinoGameVariantTable.name] = variant.name
            this[CasinoGameVariantTable.integration] = variant.integration
            this[CasinoGameVariantTable.game] = gameId
            this[CasinoGameVariantTable.providerName] = variant.providerName
            this[CasinoGameVariantTable.freeSpinEnable] = variant.freeSpinEnable
            this[CasinoGameVariantTable.freeChipEnable] = variant.freeChipEnable
            this[CasinoGameVariantTable.jackpotEnable] = variant.jackpotEnable
            this[CasinoGameVariantTable.demoEnable] = variant.demoEnable
            this[CasinoGameVariantTable.bonusBuyEnable] = variant.bonusBuyEnable
            this[CasinoGameVariantTable.locales] = variant.locales.map { it.value }
            this[CasinoGameVariantTable.platforms] = variant.platforms.map { it.name }
            this[CasinoGameVariantTable.playLines] = variant.playLines
        }

        gameVariants
    }

    override suspend fun findById(id: Long): CasinoGameVariant? = dbRead {
        CasinoGameVariantEntity.findById(id)
            ?.load(*variantChain)
            ?.toDomain()
    }

    override suspend fun findBySymbol(symbol: String): CasinoGameVariant? = dbRead {
        CasinoGameVariantEntity.find { CasinoGameVariantTable.symbol eq symbol }
            .with(*variantChain)
            .firstOrNull()?.toDomain()
    }

    override suspend fun findAllByGame(gameIdentity: Identity): List<CasinoGameVariant> = dbRead {
        val gameId = CasinoGameTable.select(CasinoGameTable.id)
            .where { CasinoGameTable.identity eq gameIdentity.value }
            .singleOrNull()?.get(CasinoGameTable.id)
            ?: return@dbRead emptyList()

        CasinoGameVariantEntity.find { CasinoGameVariantTable.game eq gameId }
            .with(*variantChain)
            .toList()
            .map { it.toDomain() }
    }

    override suspend fun findAllByIntegration(integration: String): List<CasinoGameVariant> = dbRead {
        CasinoGameVariantEntity.find { CasinoGameVariantTable.integration eq integration }
            .with(*variantChain)
            .toList()
            .map { it.toDomain() }
    }

    /**
     * The variant a launch will actually use: the provider's aggregator when it carries the game,
     * otherwise any other ACTIVE aggregator that does. The provider's aggregator is a preference,
     * not a binding — binding them meant that moving a provider made every game the new aggregator
     * lacked unopenable, even though another live aggregator was still serving it.
     */
    override suspend fun findActiveByGameIdentity(gameIdentity: Identity): CasinoGameVariant? = dbRead {
        val game = CasinoGameTable
            .join(CasinoProviderTable, JoinType.INNER, CasinoGameTable.provider, CasinoProviderTable.id)
            .join(AggregatorTable, JoinType.INNER, CasinoProviderTable.aggregator, AggregatorTable.id)
            .select(CasinoGameTable.id, AggregatorTable.integration)
            .where {
                (CasinoGameTable.identity eq gameIdentity.value) and
                    (CasinoGameTable.active eq true) and
                    (CasinoProviderTable.active eq true)
            }
            .firstOrNull()
            ?: return@dbRead null

        val preferredIntegration = game[AggregatorTable.integration]

        val candidates = CasinoGameVariantTable
            .join(
                AggregatorTable,
                JoinType.INNER,
                CasinoGameVariantTable.integration,
                AggregatorTable.integration,
            )
            .select(CasinoGameVariantTable.id, CasinoGameVariantTable.integration, AggregatorTable.id)
            .where {
                (CasinoGameVariantTable.game eq game[CasinoGameTable.id]) and (AggregatorTable.active eq true)
            }
            .map {
                Candidate(
                    variantId = it[CasinoGameVariantTable.id].value,
                    integration = it[CasinoGameVariantTable.integration],
                    aggregatorId = it[AggregatorTable.id].value,
                )
            }

        val chosen = candidates.minWithOrNull(
            compareBy({ if (it.integration == preferredIntegration) 0 else 1 }, { it.aggregatorId })
        ) ?: return@dbRead null

        CasinoGameVariantEntity.findById(chosen.variantId)
            ?.load(*variantChain)
            ?.toDomain()
    }

    private data class Candidate(val variantId: Long, val integration: String, val aggregatorId: Long)

    private fun UpdateBuilder<*>.fromDomain(gameVariant: CasinoGameVariant) {
        val gameId = domainRequireNotNull(
            CasinoGameTable.select(CasinoGameTable.id)
                .where { CasinoGameTable.identity eq gameVariant.game.identity.value }
                .singleOrNull()?.get(CasinoGameTable.id)
        ) { CasinoGameNotFoundException() }

        this[CasinoGameVariantTable.symbol] = gameVariant.symbol.value
        this[CasinoGameVariantTable.name] = gameVariant.name
        this[CasinoGameVariantTable.integration] = gameVariant.integration
        this[CasinoGameVariantTable.game] = gameId
        this[CasinoGameVariantTable.providerName] = gameVariant.providerName
        this[CasinoGameVariantTable.freeSpinEnable] = gameVariant.freeSpinEnable
        this[CasinoGameVariantTable.freeChipEnable] = gameVariant.freeChipEnable
        this[CasinoGameVariantTable.jackpotEnable] = gameVariant.jackpotEnable
        this[CasinoGameVariantTable.demoEnable] = gameVariant.demoEnable
        this[CasinoGameVariantTable.bonusBuyEnable] = gameVariant.bonusBuyEnable
        this[CasinoGameVariantTable.locales] = gameVariant.locales.map { it.value }
        this[CasinoGameVariantTable.platforms] = gameVariant.platforms.map { it.name }
        this[CasinoGameVariantTable.playLines] = gameVariant.playLines
    }
}
