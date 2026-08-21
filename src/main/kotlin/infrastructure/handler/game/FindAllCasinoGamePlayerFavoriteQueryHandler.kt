package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllCasinoGamePlayerFavoriteQuery
import application.query.game.CasinoGameView
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.table.CasinoGameFavouriteTable
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and

class FindAllCasinoGamePlayerFavoriteQueryHandler : IQueryHandler<FindAllCasinoGamePlayerFavoriteQuery, Page<CasinoGameView>> {

    override suspend fun handle(query: FindAllCasinoGamePlayerFavoriteQuery): Page<CasinoGameView> = dbRead {
        val filterCondition = query.filter.toCondition()

        val baseQuery = (CasinoGameFavouriteTable innerJoin CasinoGameTable)
            .select(CasinoGameTable.id, CasinoGameFavouriteTable.id)
            .where {
                (CasinoGameFavouriteTable.playerId eq query.playerId.value) and filterCondition
            }

        val totalItems = baseQuery.count()
        val pageable = query.pageable

        val gameIds = baseQuery
            .orderBy(CasinoGameFavouriteTable.id to SortOrder.DESC)
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .map { it[CasinoGameTable.id] }

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
