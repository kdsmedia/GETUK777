package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindCasinoGameQuery
import application.query.game.CasinoGameView
import infrastructure.persistence.dbRead
import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.entity.CasinoProviderEntity
import infrastructure.persistence.mapper.CasinoGameMapper.toDomain
import infrastructure.persistence.mapper.CasinoGameVariantMapper.toDomain
import infrastructure.persistence.table.CasinoGameTable
import infrastructure.persistence.table.CasinoGameVariantTable
import org.jetbrains.exposed.dao.with
import org.jetbrains.exposed.sql.and
import java.util.Optional

class FindCasinoGameQueryHandler : IQueryHandler<FindCasinoGameQuery, Optional<CasinoGameView>> {

    override suspend fun handle(query: FindCasinoGameQuery): Optional<CasinoGameView> = dbRead {
        val entity = CasinoGameEntity.find { CasinoGameTable.identity eq query.identity.value }
            .with(CasinoGameEntity::provider, CasinoGameEntity::collections, CasinoProviderEntity::aggregator)
            .firstOrNull() ?: return@dbRead Optional.empty()

        val variantEntity = CasinoGameVariantEntity.find {
            (CasinoGameVariantTable.game eq entity.id) and
                (CasinoGameVariantTable.integration eq entity.provider.aggregator.integration)
        }.firstOrNull()

        Optional.of(
            CasinoGameView(
                game = entity.toDomain(),
                variant = variantEntity?.toDomain(),
            )
        )
    }
}
