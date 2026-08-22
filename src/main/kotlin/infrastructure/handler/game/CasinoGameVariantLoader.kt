package infrastructure.handler.game

import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.CasinoGameVariantTable
import org.jetbrains.exposed.sql.SortOrder

/**
 * Picks the variant each game is actually served through.
 *
 * A provider's aggregator is a *preference*, not a binding: it wins whenever it carries the game,
 * and a game it does not carry falls back to any other ACTIVE aggregator that does. Binding the
 * two used to mean that moving a provider silently dropped every game the new aggregator lacked —
 * on prematch that hid 2 004 games behind twelve providers listed on both aggregators.
 *
 * A game with no variant on any active aggregator has no entry here: there is nothing to show and
 * nothing to open, which is why [CasinoGameFilterCondition] filters it out of every listing.
 *
 * Used by every game-listing query handler so the rule is defined once. Caller must already be
 * inside a `dbRead { }` / `dbTransaction { }` block.
 */
internal fun List<CasinoGameEntity>.loadVariantMap(): Map<Long, CasinoGameVariantEntity> {
    if (isEmpty()) return emptyMap()

    val ranks = activeAggregatorRanks()
    if (ranks.isEmpty()) return emptyMap()

    val preferred = associate { it.id.value to it.provider.aggregator.integration }

    return CasinoGameVariantEntity.find { CasinoGameVariantTable.game inList map { it.id } }
        .filter { it.integration in ranks }
        .groupBy { it.game.id.value }
        .mapValues { (gameId, variants) ->
            variants.minBy { variant ->
                if (variant.integration == preferred[gameId]) PREFERRED_RANK
                else ranks.getValue(variant.integration)
            }
        }
}

internal fun CasinoGameEntity.variantFrom(map: Map<Long, CasinoGameVariantEntity>): CasinoGameVariantEntity? =
    map[id.value]

/** Active aggregators by integration, best first. Ties among fallbacks resolve by insertion age so
 *  the same game never flips between aggregators from one listing to the next. */
private fun activeAggregatorRanks(): Map<String, Int> =
    AggregatorTable
        .select(AggregatorTable.integration)
        .where { AggregatorTable.active eq true }
        .orderBy(AggregatorTable.id to SortOrder.ASC)
        .mapIndexed { index, row -> row[AggregatorTable.integration] to index }
        .toMap()

/** Beats every fallback rank, which start at 0. */
private const val PREFERRED_RANK = -1
