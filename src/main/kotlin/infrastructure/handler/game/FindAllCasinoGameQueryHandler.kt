package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllCasinoGameQuery
import application.query.game.CasinoGameView
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import org.jetbrains.exposed.dao.with

class FindAllCasinoGameQueryHandler : IQueryHandler<FindAllCasinoGameQuery, Page<CasinoGameView>> {

    override suspend fun handle(query: FindAllCasinoGameQuery): Page<CasinoGameView> = dbRead {
        val filter = query.filter
        val baseQuery = CasinoGameEntity.find { filter.toCondition() }
        val totalItems = baseQuery.count()
        val pageable = query.pageable

        val entities = baseQuery
            .orderBy(*filter.toOrdering())
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .with(CasinoGameEntity::provider, CasinoGameEntity::collections, CasinoProviderEntity::aggregator)
            .toList()

        val variantMap = entities.loadVariantMap()

        val items = entities.map { entity ->
            CasinoGameView(
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
