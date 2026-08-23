package infrastructure.handler.collection

import application.IQueryHandler
import application.query.collection.FindAllCollectionQuery
import domain.model.Collection
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.mapper.CollectionMapper.toCollection
import infrastructure.persistence.search.SearchIndexes
import infrastructure.persistence.table.CollectionTable
import infrastructure.persistence.table.CasinoGameCollectionTable
import infrastructure.persistence.table.CasinoGameTable
import infrastructure.persistence.table.CasinoProviderTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll

class FindAllCollectionQueryHandler : IQueryHandler<FindAllCollectionQuery, Page<Collection>> {

    override suspend fun handle(query: FindAllCollectionQuery): Page<Collection> = dbRead {
        val filterCondition = buildFilterCondition(query)
        val pageable = query.pageable

        val totalItems = CollectionTable.selectAll().where { filterCondition }.count()

        val items = CollectionTable
            .selectAll()
            .where { filterCondition }
            .orderBy(
                *SearchIndexes.collections.relevanceOrdering(query.query),
                CollectionTable.sortOrder to SortOrder.ASC,
            )
            .limit(pageable.sizeReal)
            .offset(pageable.offset)
            .map { it.toCollection() }

        Page(
            items = items,
            totalPages = pageable.getTotalPages(totalItems),
            totalItems = totalItems,
            currentPage = pageable.pageReal,
        )
    }

    private fun buildFilterCondition(query: FindAllCollectionQuery): Op<Boolean> {
        val conditions = buildList {
            if (query.query.isNotBlank()) {
                add(SearchIndexes.collections.matches(query.query))
            }
            query.active?.let { add(Op.build { CollectionTable.active eq it }) }

            // The collection's OWN tags (same ANY-of semantics and JSON-LIKE trick as the game filter).
            if (query.inTags.isNotEmpty()) {
                add(query.inTags.map { tag ->
                    Op.build { CollectionTable.tags.castTo<String>(TextColumnType()) like "%\"$tag\"%" }
                }.reduce { acc, op -> acc or op })
            }

            if (query.inProviderIdentities.isNotEmpty()) {
                add(exists(
                    CasinoGameCollectionTable
                        .join(CasinoGameTable, JoinType.INNER, CasinoGameCollectionTable.game, CasinoGameTable.id)
                        .select(CasinoGameCollectionTable.collection)
                        .where {
                            (CasinoGameCollectionTable.collection eq CollectionTable.id) and
                                    (CasinoGameTable.provider inSubQuery (
                                            CasinoProviderTable
                                                .select(CasinoProviderTable.id)
                                                .where { CasinoProviderTable.identity inList query.inProviderIdentities.map { it.value } }
                                            ))
                        }
                ))
            }
        }
        return conditions.reduceOrNull { acc, op -> acc and op } ?: Op.TRUE
    }
}
