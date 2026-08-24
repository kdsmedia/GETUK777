package infrastructure.handler.game

import infrastructure.persistence.entity.CasinoGameEntity
import infrastructure.persistence.entity.CasinoGameVariantEntity
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.CasinoGameVariantTable

/**
 * Picks the variant each game is served through: the one from the aggregator set on the game's
 * PROVIDER.
 *
 * The provider's aggregator is a binding. A game it does not carry has no entry here even when
 * another aggregator carries the same game — there is nothing to show and nothing to open, which
 * is why [CasinoGameFilterCondition] filters such a game out of every listing. Launching resolves
 * the variant by the same rule, so a game is listed exactly when it can be opened.
 *
 * A provider whose aggregator is switched off has no serving variant either, so its whole
 * catalogue leaves the shelf rather than sitting there unopenable.
 *
 * Used by every game-listing query handler so the rule is defined once. Caller must already be
 * inside a `dbRead { }` / `dbTransaction { }` block.
 */
internal fun List<CasinoGameEntity>.loadVariantMap(): Map<Long, CasinoGameVariantEntity> {
    if (isEmpty()) return emptyMap()

    val active = activeAggregatorIntegrations()
    if (active.isEmpty()) return emptyMap()

    val boundTo = associate { it.id.value to it.provider.aggregator.integration }

    return CasinoGameVariantEntity.find { CasinoGameVariantTable.game inList map { it.id } }
        .filter { it.integration in active && it.integration == boundTo[it.game.id.value] }
        .groupBy { it.game.id.value }
        // A feed can list the same game twice under one integration; lowest id keeps the choice
        // stable so a game never flips between rows from one listing to the next.
        .mapValues { (_, variants) -> variants.minBy { it.id.value } }
}

internal fun CasinoGameEntity.variantFrom(map: Map<Long, CasinoGameVariantEntity>): CasinoGameVariantEntity? =
    map[id.value]

/** Integrations of the aggregators that are switched on. */
private fun activeAggregatorIntegrations(): Set<String> =
    AggregatorTable
        .select(AggregatorTable.integration)
        .where { AggregatorTable.active eq true }
        .mapTo(mutableSetOf()) { it[AggregatorTable.integration] }
