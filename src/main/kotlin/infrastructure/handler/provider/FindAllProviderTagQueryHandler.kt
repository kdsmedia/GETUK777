package infrastructure.handler.provider

import application.IQueryHandler
import application.query.provider.FindAllProviderTagQuery
import domain.vo.Page
import infrastructure.persistence.dbRead
import infrastructure.persistence.table.ProviderTable

class FindAllProviderTagQueryHandler : IQueryHandler<FindAllProviderTagQuery, Page<String>> {

    override suspend fun handle(query: FindAllProviderTagQuery): Page<String> = dbRead {
        // Same in-memory distinct as FindAllGameTagQueryHandler: the provider
        // catalog is tiny and the json array unnest would be dialect-specific.
        val tags = ProviderTable
            .select(ProviderTable.tags)
            .where { ProviderTable.active eq true }
            .flatMap { it[ProviderTable.tags] }
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
