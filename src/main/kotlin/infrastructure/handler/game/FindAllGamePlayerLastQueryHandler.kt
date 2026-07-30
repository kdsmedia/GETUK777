package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllGamePlayerLastQuery
import application.query.game.GameView
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.GameEntity
import infrastructure.persistence.entity.ProviderEntity
import infrastructure.persistence.mapper.GameMapper.toDomain
import infrastructure.persistence.mapper.GameVariantMapper.toDomain
import infrastructure.persistence.table.GameVariantTable
import infrastructure.persistence.table.SessionTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.max

class FindAllGamePlayerLastQueryHandler : IQueryHandler<FindAllGamePlayerLastQuery, Page<GameView>> {

    override suspend fun handle(query: FindAllGamePlayerLastQuery): Page<GameView> = dbRead {
        // Sessions carry no timestamp column — the monotonic PK is the recency order,
        // same convention the favourites listing uses.
        val lastSessionId = SessionTable.id.max()

        val baseQuery = (SessionTable innerJoin GameVariantTable)
            .select(GameVariantTable.game, lastSessionId)
            .where { SessionTable.playerId eq query.playerId.value }
            .groupBy(GameVariantTable.game)

        val totalItems = baseQuery.count()
        val pageable = query.pageable

        val gameIds = baseQuery
            .orderBy(lastSessionId to SortOrder.DESC)
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .map { it[GameVariantTable.game] }

        val entities = GameEntity.forEntityIds(gameIds)
            .with(GameEntity::provider, GameEntity::collections, ProviderEntity::aggregator)
            .toList()

        val variantMap = entities.loadVariantMap()

        val viewsById = entities.associate { entity ->
            entity.id to GameView(
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
