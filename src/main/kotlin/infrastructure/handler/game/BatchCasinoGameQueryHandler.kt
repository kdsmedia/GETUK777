package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.BatchCasinoGameQuery
import application.query.game.CasinoGameView
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder

class BatchCasinoGameQueryHandler : IQueryHandler<BatchCasinoGameQuery, List<CasinoGameView>> {

    override suspend fun handle(query: BatchCasinoGameQuery): List<CasinoGameView> = dbRead {
        val identityValues = query.identities.map { it.value }

        val entities = CasinoGameEntity.find { CasinoGameTable.identity inList identityValues }
            .orderBy(CasinoGameTable.sortOrder to SortOrder.ASC)
            .with(CasinoGameEntity::provider, CasinoGameEntity::collections, CasinoProviderEntity::aggregator)
            .toList()

        val variantMap = entities.loadVariantMap()

        entities.map { entity ->
            CasinoGameView(
                game = entity.toDomain(),
                variant = entity.variantFrom(variantMap)?.toDomain(),
            )
        }
    }
}
