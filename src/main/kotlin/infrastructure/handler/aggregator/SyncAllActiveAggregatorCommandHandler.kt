package infrastructure.handler.aggregator

import application.ICommandHandler
import application.command.aggregator.SyncAllActiveAggregatorCommand
import domain.repository.IAggregatorRepository
import application.usecase.SyncAggregatorUsecase
import org.slf4j.LoggerFactory

class SyncAllActiveAggregatorCommandHandler(
    private val aggregatorRepository: IAggregatorRepository,
    private val syncAggregatorUsecase: SyncAggregatorUsecase
) : ICommandHandler<SyncAllActiveAggregatorCommand, Unit> {

    private val logger = LoggerFactory.getLogger(SyncAllActiveAggregatorCommandHandler::class.java)

    override suspend fun handle(command: SyncAllActiveAggregatorCommand): Result<Unit> = runCatching {
        val all = aggregatorRepository.findAll()
        val aggregators = all.filter { it.active }

        logger.info("Aggregator sync: {} active of {} total", aggregators.size, all.size)
        aggregators.forEach { logger.info("  active -> identity={} integration={}", it.identity.value, it.integration) }

        aggregators.forEachIndexed { index, aggregator ->
            logger.info(
                "[{}/{}] syncing identity={} integration={}",
                index + 1, aggregators.size, aggregator.identity.value, aggregator.integration,
            )
            syncAggregatorUsecase(aggregator)
        }

        logger.info("Aggregator sync finished: {} aggregators processed", aggregators.size)
    }

}