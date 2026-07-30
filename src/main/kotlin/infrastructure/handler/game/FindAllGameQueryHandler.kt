package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllGameQuery
import application.query.game.GameView
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.GameEntity
import infrastructure.persistence.entity.ProviderEntity
import infrastructure.persistence.mapper.GameMapper.toDomain
import infrastructure.persistence.mapper.GameVariantMapper.toDomain
import org.jetbrains.exposed.dao.with

class FindAllGameQueryHandler : IQueryHandler<FindAllGameQuery, Page<GameView>> {

    override suspend fun handle(query: FindAllGameQuery): Page<GameView> = dbRead {
        val filter = query.filter
        val baseQuery = GameEntity.find { filter.toCondition() }
        val totalItems = baseQuery.count()
        val pageable = query.pageable

        val entities = baseQuery
            .orderBy(*filter.toOrdering())
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .with(GameEntity::provider, GameEntity::collections, ProviderEntity::aggregator)
            .toList()

        val variantMap = entities.loadVariantMap()

        val items = entities.map { entity ->
            GameView(
                game = entity.toDomain(),
                variant = entity.variantFrom(variantMap)?.toDomain(),
            )
        }

        Page(
            items = items,
            totalPages = pageable.getTotalPages(totalItems),
            totalItems = totalItems,
            currentPage = pageable.pageReal,
        )
    }
}
