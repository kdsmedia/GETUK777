package infrastructure.handler.sportbook

import application.IQueryHandler
import application.port.factory.IAggregatorFactory
import application.query.sportbook.InitSportbookQuery
import application.query.sportbook.SportbookInit
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.model.AggregatorType
import domain.repository.IAggregatorRepository

class InitSportbookHandler(
    private val aggregatorRepository: IAggregatorRepository,
    private val aggregatorFactory: IAggregatorFactory,
) : IQueryHandler<InitSportbookQuery, SportbookInit> {

    override suspend fun handle(query: InitSportbookQuery): SportbookInit {
        val aggregator = domainRequireNotNull(
            aggregatorRepository.findFirstActiveByType(AggregatorType.SPORTBOOK)
        ) { AggregatorNotFoundException() }

        return SportbookInit(
            integration = aggregator.integration,
            data = aggregatorFactory.createSportbookAdapter(aggregator).init(),
        )
    }
}
