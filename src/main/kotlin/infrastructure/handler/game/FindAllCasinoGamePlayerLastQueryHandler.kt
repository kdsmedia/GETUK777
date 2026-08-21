package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllCasinoGamePlayerLastQuery
import application.query.game.CasinoGameView
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.table.CasinoGameVariantTable
import infrastructure.persistence.table.CasinoSessionTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.max

class FindAllCasinoGamePlayerLastQueryHandler : IQueryHandler<FindAllCasinoGamePlayerLastQuery, Page<CasinoGameView>> {

    override suspend fun handle(query: FindAllCasinoGamePlayerLastQuery): Page<CasinoGameView> = dbRead {
        // Sessions carry no timestamp column — the monotonic PK is the recency order,
        // same convention the favourites listing uses.
        val lastSessionId = CasinoSessionTable.id.max()

        val baseQuery = (CasinoSessionTable innerJoin CasinoGameVariantTable)
            .select(CasinoGameVariantTable.game, lastSessionId)
            .where { CasinoSessionTable.playerId eq query.playerId.value }
            .groupBy(CasinoGameVariantTable.game)

        val totalItems = baseQuery.count()
        val pageable = query.pageable

        val gameIds = baseQuery
            .orderBy(lastSessionId to SortOrder.DESC)
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .map { it[CasinoGameVariantTable.game] }

        val entities = CasinoGameEntity.forEntityIds(gameIds)
            .with(CasinoGameEntity::provider, CasinoGameEntity::collections, CasinoProviderEntity::aggregator)
            .toList()

        val variantMap = entities.loadVariantMap()

        val viewsById = entities.associate { entity ->
            entity.id to CasinoGameView(
                game = entity.toDomain(),
                variant = entity.variantFrom(variantMap)?.toDomain(),
            )
        }

        val items = gameIds.mapNotNull { id -> viewsById[id] }

        Page(
            items = items,
            totalPages = pageable.getTotalPages(totalItems),
            totalItems = totalItems,
            currentPage = pageable.pageReal,
        )
    }
}
