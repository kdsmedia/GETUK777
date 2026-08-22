package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindCasinoGameQuery
import application.query.game.CasinoGameView
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.table.CasinoGameTable
import org.jetbrains.exposed.dao.with
import java.util.Optional

class FindCasinoGameQueryHandler : IQueryHandler<FindCasinoGameQuery, Optional<CasinoGameView>> {

    override suspend fun handle(query: FindCasinoGameQuery): Optional<CasinoGameView> = dbRead {
        val entity = CasinoGameEntity.find { CasinoGameTable.identity eq query.identity.value }
            .with(CasinoGameEntity::provider, CasinoGameEntity::collections, CasinoProviderEntity::aggregator)
            .firstOrNull() ?: return@dbRead Optional.empty()

        // Same rule as every listing: the provider's aggregator first, any other active one after.
        val variantEntity = entity.variantFrom(listOf(entity).loadVariantMap())

        Optional.of(
            CasinoGameView(
                game = entity.toDomain(),
                variant = variantEntity?.toDomain(),
            )
        )
    }
}
