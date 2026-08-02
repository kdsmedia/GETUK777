package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllActiveRtpGameQuery
import application.query.game.GameRtpType
import application.query.game.GameView
import domain.model.Game
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.GameEntity
import infrastructure.persistence.entity.ProviderEntity
import infrastructure.persistence.mapper.GameMapper.toDomain
import infrastructure.persistence.mapper.GameVariantMapper.toDomain
import infrastructure.persistence.table.GameTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and

class FindAllActiveRtpGameQueryHandler : IQueryHandler<FindAllActiveRtpGameQuery, Page<GameView>> {

    override suspend fun handle(query: FindAllActiveRtpGameQuery): Page<GameView> = dbRead {
        val rtpCondition = when (query.type) {
            GameRtpType.HOT -> Op.build { GameTable.rtp greater Game.DEFAULT_RTP }
            GameRtpType.COLD -> Op.build { GameTable.rtp less Game.DEFAULT_RTP }
        }
        val rtpOrder = when (query.type) {
            GameRtpType.HOT -> SortOrder.DESC
            GameRtpType.COLD -> SortOrder.ASC
        }

        val baseQuery = GameEntity.find {
            query.filter.toCondition() and Op.build { GameTable.active eq true } and rtpCondition
        }
        val totalItems = baseQuery.count()
        val pageable = query.pageable

        // id tiebreaker: same reason as GameFilter.toOrdering — equal (rtp, sortOrder)
        // keys give unstable pagination.
        val entities = baseQuery
            .orderBy(
                GameTable.rtp to rtpOrder,
                GameTable.sortOrder to SortOrder.ASC,
                GameTable.id to SortOrder.ASC,
            )
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
