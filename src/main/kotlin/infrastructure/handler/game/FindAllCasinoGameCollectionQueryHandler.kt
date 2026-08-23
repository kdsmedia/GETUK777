package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllCasinoGameCollectionQuery
import application.query.game.CasinoGameView
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.table.CollectionTable
import infrastructure.persistence.table.CasinoGameCollectionTable
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and

class FindAllCasinoGameCollectionQueryHandler : IQueryHandler<FindAllCasinoGameCollectionQuery, Page<CasinoGameView>> {

    override suspend fun handle(query: FindAllCasinoGameCollectionQuery): Page<CasinoGameView> = dbRead {
        val filterCondition = query.filter.toCondition()
        val collectionIdentity = query.collection.value

        // Phase 1 — page the join table ordered by per-collection sort order.
        val baseQuery = (CasinoGameCollectionTable innerJoin CasinoGameTable innerJoin CollectionTable)
            .select(CasinoGameTable.id, CasinoGameCollectionTable.sortOrder)
            .where {
                (CollectionTable.identity eq collectionIdentity) and filterCondition
            }

        val totalItems = baseQuery.count()
        val pageable = query.pageable

        val gameIds = baseQuery
            .orderBy(
                *query.filter.relevanceOrdering(),
                CasinoGameCollectionTable.sortOrder to SortOrder.ASC,
                CasinoGameTable.id to SortOrder.ASC,
            )
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .map { it[CasinoGameTable.id] }

        // Phase 2 — load the game entities in any order, then preserve the
        // page order in memory via a lookup map.
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
