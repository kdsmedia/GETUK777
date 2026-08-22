package infrastructure.persistence.repository

import domain.repository.ICasinoProviderRepository
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.exception.notfound.CasinoProviderNotFoundException
import domain.model.CasinoProvider
import domain.vo.Identity
import domain.vo.Page
import domain.vo.Pageable
import infrastructure.persistence.dbRead
import infrastructure.persistence.dbTransaction
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoProviderMapper.toCasinoProvider
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.CasinoProviderTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.upsert

class CasinoProviderRepositoryImpl : ICasinoProviderRepository {

    override suspend fun save(provider: CasinoProvider): CasinoProvider = dbTransaction {
        val aggregatorId = domainRequireNotNull(
            AggregatorTable.select(AggregatorTable.id)
                .where { AggregatorTable.identity eq provider.aggregator.identity.value }
                .singleOrNull()?.get(AggregatorTable.id)
        ) { AggregatorNotFoundException() }

        CasinoProviderTable.upsert(keys = arrayOf(CasinoProviderTable.identity)) {
            it[identity] = provider.identity.value
            it[name] = provider.name
            it[images] = provider.images.data
            it[sortOrder] = provider.order
            it[active] = provider.active
            it[aggregator] = aggregatorId
            it[blockedCountry] = provider.blockedCountry.map { it.value }
            it[tags] = provider.tags
            it[aliases] = provider.aliases
        }

        provider
    }

    override suspend fun saveAll(providers: List<CasinoProvider>): List<CasinoProvider> = dbTransaction {
        val aggregatorIdentities = providers.map { it.aggregator.identity.value }.distinct()
        val aggregatorMap = AggregatorTable.select(AggregatorTable.id, AggregatorTable.identity)
            .where { AggregatorTable.identity inList aggregatorIdentities }
            .associate { it[AggregatorTable.identity] to it[AggregatorTable.id] }

        CasinoProviderTable.batchUpsert(providers, keys = arrayOf(CasinoProviderTable.identity)) { provider ->
            val aggregatorId = domainRequireNotNull(aggregatorMap[provider.aggregator.identity.value]) {
                AggregatorNotFoundException()
            }

            this[CasinoProviderTable.identity] = provider.identity.value
            this[CasinoProviderTable.name] = provider.name
            this[CasinoProviderTable.images] = provider.images.data
            this[CasinoProviderTable.sortOrder] = provider.order
            this[CasinoProviderTable.active] = provider.active
            this[CasinoProviderTable.aggregator] = aggregatorId
            this[CasinoProviderTable.blockedCountry] = provider.blockedCountry.map { it.value }
            this[CasinoProviderTable.tags] = provider.tags
            this[CasinoProviderTable.aliases] = provider.aliases
        }

        providers
    }

    override suspend fun findAll(): List<CasinoProvider> = dbRead {
        CasinoProviderTable
            .join(AggregatorTable, JoinType.INNER, CasinoProviderTable.aggregator, AggregatorTable.id)
            .selectAll()
            .map { it.toCasinoProvider() }
    }

    override suspend fun findByIdentity(identity: Identity): CasinoProvider? = dbRead {
        CasinoProviderTable
            .join(AggregatorTable, JoinType.INNER, CasinoProviderTable.aggregator, AggregatorTable.id)
            .selectAll()
            .where { CasinoProviderTable.identity eq identity.value }
            .singleOrNull()
            ?.toCasinoProvider()
    }

    override suspend fun findAll(pageable: Pageable): Page<CasinoProvider> = dbRead {
        val totalItems = CasinoProviderTable.selectAll().count()
        val items = CasinoProviderTable
            .join(AggregatorTable, JoinType.INNER, CasinoProviderTable.aggregator, AggregatorTable.id)
            .selectAll()
            .limit(pageable.sizeReal, pageable.offset)
            .map { it.toCasinoProvider() }

        Page(
            items = items,
            totalPages = pageable.getTotalPages(totalItems),
            totalItems = totalItems,
            currentPage = pageable.pageReal,
        )
    }

    override suspend fun addImage(identity: Identity, key: String, url: String) {
        dbTransaction {
            val entity = domainRequireNotNull(
                CasinoProviderEntity.find { CasinoProviderTable.identity eq identity.value }.firstOrNull()
            ) { CasinoProviderNotFoundException() }
            entity.images = entity.images.toMutableMap().apply { put(key, url) }
        }
    }
}
