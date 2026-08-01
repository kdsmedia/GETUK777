package infrastructure.handler.game

import application.query.game.GameFilter
import infrastructure.persistence.table.CollectionTable
import infrastructure.persistence.table.GameCollectionTable
import infrastructure.persistence.table.GameTable
import infrastructure.persistence.table.GameVariantTable
import infrastructure.persistence.table.ProviderTable
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.wrapAsExpression

fun GameFilter.toCondition(): Op<Boolean> {
    val conditions = buildList<Op<Boolean>> {
        if (query.isNotBlank()) {
            val pattern = "%${query.lowercase()}%"
            add(Op.build {
                (GameTable.identity like pattern) or (GameTable.name like pattern)
            })
        }

        active?.let {
            add(Op.build { GameTable.active eq it })
        }

        bonusBetEnable?.let {
            add(Op.build { GameTable.bonusBetEnable eq it })
        }

        bonusWageringEnabled?.let {
            add(Op.build { GameTable.bonusWageringEnable eq it })
        }

        provider?.let { providerIdentity ->
            add(Op.build {
                GameTable.provider inSubQuery (
                    ProviderTable
                        .select(ProviderTable.id)
                        .where { ProviderTable.identity eq providerIdentity.value }
                )
            })
        }

        collection?.let { collectionIdentity ->
            add(Op.build {
                GameTable.id inSubQuery (
                    (GameCollectionTable innerJoin CollectionTable)
                        .select(GameCollectionTable.game)
                        .where { CollectionTable.identity eq collectionIdentity.value }
                )
            })
        }

        val variantConditions = buildList<Op<Boolean>> {
            freeSpinEnable?.let {
                add(Op.build { GameVariantTable.freeSpinEnable eq it })
            }
            freeChipEnable?.let {
                add(Op.build { GameVariantTable.freeChipEnable eq it })
            }
            jackpotEnable?.let {
                add(Op.build { GameVariantTable.jackpotEnable eq it })
            }
            demoEnable?.let {
                add(Op.build { GameVariantTable.demoEnable eq it })
            }
            bonusBuyEnable?.let {
                add(Op.build { GameVariantTable.bonusBuyEnable eq it })
            }
        }

        if (variantConditions.isNotEmpty()) {
            val variantCondition = variantConditions.reduce { acc, op -> acc and op }
            add(exists(
                GameVariantTable
                    .select(GameVariantTable.id)
                    .where {
                        (GameVariantTable.game eq GameTable.id) and variantCondition
                    }
            ))
        }

        if (inTags.isNotEmpty()) {
            add(inTags.map { tag ->
                Op.build { GameTable.tags.castTo<String>(TextColumnType()) like "%\"$tag\"%" }
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
fun GameFilter.toOrdering(): Array<Pair<Expression<*>, SortOrder>> {
    // id tiebreaker: sortOrder is not unique (bulk-synced games share 0), and equal keys
    // give unstable pagination — a game could repeat or vanish across pages.
    val collectionIdentity = collection
        ?: return arrayOf(GameTable.sortOrder to SortOrder.ASC, GameTable.id to SortOrder.ASC)

    val railPosition = wrapAsExpression<Int>(
        (GameCollectionTable innerJoin CollectionTable)
            .select(GameCollectionTable.sortOrder)
            .where {
                (GameCollectionTable.game eq GameTable.id) and
                    (CollectionTable.identity eq collectionIdentity.value)
            }
    )

    return arrayOf(railPosition to SortOrder.ASC, GameTable.id to SortOrder.ASC)
}
