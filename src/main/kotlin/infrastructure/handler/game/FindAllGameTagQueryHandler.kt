package infrastructure.handler.game

import application.IQueryHandler
import application.query.game.FindAllGameTagQuery
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.table.GameTable

class FindAllGameTagQueryHandler : IQueryHandler<FindAllGameTagQuery, Page<String>> {

    override suspend fun handle(query: FindAllGameTagQuery): Page<String> = dbRead {
        // Tags live as a denormalized json array per game, so the distinct set is
        // assembled in memory: the scan reads one small column over the catalog,
        // while unnesting the array in SQL would tie the handler to the Postgres
        // dialect. Only ACTIVE games contribute — a tag no live game carries would
        // produce an empty listing when used as a filter.
        val tags = GameTable
            .select(GameTable.tags)
            .where { GameTable.active eq true }
            .flatMap { it[GameTable.tags] }
            .distinct()
            .sorted()

        val pageable = query.pageable
        val fromIndex = pageable.offset.toInt().coerceAtMost(tags.size)
        val toIndex = (fromIndex + pageable.sizeReal).coerceAtMost(tags.size)

        Page(
            items = tags.subList(fromIndex, toIndex),
            totalPages = pageable.getTotalPages(tags.size.toLong()),
            totalItems = tags.size.toLong(),
            currentPage = pageable.pageReal,
        )
    }
}
