package application.usecase

import application.port.external.IEventPublisherPort
import application.port.factory.IAggregatorFactory
import domain.event.SportbookOpenEvent
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.model.AggregatorType
import domain.model.SportbookSession
import domain.repository.IAggregatorRepository
import domain.repository.ISportbookSessionRepository
import domain.vo.Currency
import domain.vo.PlayerId
import domain.vo.SportbookSessionToken
import org.slf4j.LoggerFactory

class OpenSportbookUsecase(
    private val aggregatorRepository: IAggregatorRepository,
    private val sessionRepository: ISportbookSessionRepository,
    private val aggregatorFactory: IAggregatorFactory,
    private val eventPublisher: IEventPublisherPort,
) {

    private val logger = LoggerFactory.getLogger(OpenSportbookUsecase::class.java)

    suspend operator fun invoke(playerId: PlayerId, currency: Currency): Result<SportbookSession> = runCatching {
        val aggregator = domainRequireNotNull(
            aggregatorRepository.findFirstActiveByType(AggregatorType.SPORTBOOK)
        ) { AggregatorNotFoundException() }

        logger.info("Opening sportbook: player={} aggregator={}", playerId.value, aggregator.identity.value)

        val adapter = aggregatorFactory.createSportbookAdapter(aggregator)

        val session = SportbookSession(
            token = SportbookSessionToken(generateBase24Token()),
            playerId = playerId,
            currency = currency,
            aggregator = aggregator,
            data = emptyMap(),
        )

        // Adapter work (network etc.) happens strictly BEFORE the DB transaction.
        val data = adapter.open(session)

        val savedSession = sessionRepository.save(session.copy(data = data))

        eventPublisher.publish(SportbookOpenEvent(savedSession))

        savedSession
    }

    private fun generateBase24Token(): String = buildString(TOKEN_LENGTH) {
        repeat(TOKEN_LENGTH) {
            append(BASE24_CHARS.random())
        }
    }

    private companion object {
        const val BASE24_CHARS = "BCDFGHJKMPQRTVWXY2346789"

        const val TOKEN_LENGTH = 32
    }
}
