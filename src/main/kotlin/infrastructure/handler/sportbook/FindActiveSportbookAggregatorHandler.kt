package infrastructure.handler.sportbook

import application.IQueryHandler
import application.query.sportbook.FindActiveSportbookAggregatorQuery
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.model.Aggregator
import domain.model.AggregatorType
import domain.repository.IAggregatorRepository

class FindActiveSportbookAggregatorHandler(
    private val aggregatorRepository: IAggregatorRepository,
) : IQueryHandler<FindActiveSportbookAggregatorQuery, Aggregator> {

    override suspend fun handle(query: FindActiveSportbookAggregatorQuery): Aggregator =
        domainRequireNotNull(
            aggregatorRepository.findFirstActiveByType(AggregatorType.SPORTBOOK)
        ) { AggregatorNotFoundException() }
}
