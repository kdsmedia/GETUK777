package infrastructure.handler.game

import application.query.game.CasinoGameFilter
import infrastructure.persistence.table.AggregatorTable
import infrastructure.persistence.table.CollectionTable
import infrastructure.persistence.table.CasinoGameCollectionTable
import infrastructure.persistence.table.CasinoGameTable
import infrastructure.persistence.table.CasinoGameVariantTable
import infrastructure.persistence.table.CasinoProviderTable
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.wrapAsExpression

/**
 * A game is only listable through the variant its provider's aggregator serves. Everything the
 * storefront renders about a game — symbol, platforms, demo, paylines — is read off that variant,
 * and launching resolves it the same way. With no matching variant there is nothing to show and
 * nothing to open: the game would render with every variant field at its default (demo false,
 * empty platforms, zero paylines) and answer `CasinoGame not found` on click. Moving a provider to an
 * aggregator that does not carry part of its catalog is exactly when this happens.
 */
private fun servedByItsAggregator(): Op<Boolean> = exists(
    CasinoGameVariantTable
        .join(CasinoProviderTable, JoinType.INNER, CasinoGameTable.provider, CasinoProviderTable.id)
        .join(AggregatorTable, JoinType.INNER, CasinoProviderTable.aggregator, AggregatorTable.id)
        .select(CasinoGameVariantTable.id)
        .where {
            (CasinoGameVariantTable.game eq CasinoGameTable.id) and
                (CasinoGameVariantTable.integration eq AggregatorTable.integration)
        }
)

fun CasinoGameFilter.toCondition(): Op<Boolean> {
    val conditions = buildList<Op<Boolean>> {
        add(servedByItsAggregator())

        if (query.isNotBlank()) {
            val pattern = "%${query.lowercase()}%"
            add(Op.build {
                (CasinoGameTable.identity like pattern) or (CasinoGameTable.name like pattern)
            })
        }

        active?.let {
            add(Op.build { CasinoGameTable.active eq it })
        }

        bonusBetEnable?.let {
            add(Op.build { CasinoGameTable.bonusBetEnable eq it })
        }

        bonusWageringEnabled?.let {
            add(Op.build { CasinoGameTable.bonusWageringEnable eq it })
        }

        provider?.let { providerIdentity ->
            add(Op.build {
                CasinoGameTable.provider inSubQuery (
                    CasinoProviderTable
                        .select(CasinoProviderTable.id)
                        .where { CasinoProviderTable.identity eq providerIdentity.value }
                )
            })
        }

        collection?.let { collectionIdentity ->
            add(Op.build {
                CasinoGameTable.id inSubQuery (
                    (CasinoGameCollectionTable innerJoin CollectionTable)
                        .select(CasinoGameCollectionTable.game)
                        .where { CollectionTable.identity eq collectionIdentity.value }
                )
            })
        }

        val variantConditions = buildList<Op<Boolean>> {
            freeSpinEnable?.let {
                add(Op.build { CasinoGameVariantTable.freeSpinEnable eq it })
            }
            freeChipEnable?.let {
                add(Op.build { CasinoGameVariantTable.freeChipEnable eq it })
            }
            jackpotEnable?.let {
                add(Op.build { CasinoGameVariantTable.jackpotEnable eq it })
            }
            demoEnable?.let {
                add(Op.build { CasinoGameVariantTable.demoEnable eq it })
            }
            bonusBuyEnable?.let {
                add(Op.build { CasinoGameVariantTable.bonusBuyEnable eq it })
            }
        }

        if (variantConditions.isNotEmpty()) {
            val variantCondition = variantConditions.reduce { acc, op -> acc and op }
            add(exists(
                CasinoGameVariantTable
                    .select(CasinoGameVariantTable.id)
                    .where {
                        (CasinoGameVariantTable.game eq CasinoGameTable.id) and variantCondition
                    }
            ))
        }

        if (inTags.isNotEmpty()) {
            add(inTags.map { tag ->
                Op.build { CasinoGameTable.tags.castTo<String>(TextColumnType()) like "%\"$tag\"%" }
            }.reduce { acc, op -> acc or op })
        }
    }

    return conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
}

/**
 * Ordering that belongs with the filter: a collection-scoped listing IS a lobby rail,
 * so it follows the curated per-collection position instead of the catalog-wide one.
 * `game_collections` is keyed by (game, collection), so the correlated lookup resolves
 * to at most one row per game and needs no aggregate or DISTINCT.
 */
fun CasinoGameFilter.toOrdering(): Array<Pair<Expression<*>, SortOrder>> {
    // id tiebreaker: sortOrder is not unique (bulk-synced games share 0), and equal keys
    // give unstable pagination — a game could repeat or vanish across pages.
    val collectionIdentity = collection
        ?: return arrayOf(CasinoGameTable.sortOrder to SortOrder.ASC, CasinoGameTable.id to SortOrder.ASC)

    val railPosition = wrapAsExpression<Int>(
        (CasinoGameCollectionTable innerJoin CollectionTable)
            .select(CasinoGameCollectionTable.sortOrder)
            .where {
                (CasinoGameCollectionTable.game eq CasinoGameTable.id) and
                    (CollectionTable.identity eq collectionIdentity.value)
            }
    )

    return arrayOf(railPosition to SortOrder.ASC, CasinoGameTable.id to SortOrder.ASC)
}
