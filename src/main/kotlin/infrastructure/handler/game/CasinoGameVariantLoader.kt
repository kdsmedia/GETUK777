package infrastructure.handler.game

import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.table.CasinoGameVariantTable
import org.jetbrains.exposed.sql.and

/**
 * Loads the active [CasinoGameVariantEntity] (if any) for each game in the receiver list,
 * matching variants to their game by `(game.id, provider.aggregator.integration)`.
 *
 * Used by every game-listing query handler so the lookup is defined once. Caller must
 * already be inside a `dbRead { }` / `dbTransaction { }` block.
 */
internal fun List<CasinoGameEntity>.loadVariantMap(): Map<Pair<Long, String>, CasinoGameVariantEntity> {
    if (isEmpty()) return emptyMap()
    val gameIds = map { it.id }
    val integrations = map { it.provider.aggregator.integration }.distinct()
    return CasinoGameVariantEntity.find {
        (CasinoGameVariantTable.game inList gameIds) and (CasinoGameVariantTable.integration inList integrations)
    }.associateBy { it.game.id.value to it.integration }
}

internal fun CasinoGameEntity.variantFrom(map: Map<Pair<Long, String>, CasinoGameVariantEntity>): CasinoGameVariantEntity? =
    map[id.value to provider.aggregator.integration]
