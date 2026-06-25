package application.usecase

import application.port.external.IEventPublisherPort
import application.port.external.LotterySocketSession
import application.port.factory.IAggregatorFactory
import domain.event.SessionEvent
import domain.exception.DomainException
import domain.exception.domainRequireNotNull
import domain.exception.notfound.GameNotFoundException
import domain.model.Platform
import domain.repository.IGameVariantRepository
import domain.repository.ISessionRepository
import domain.service.SessionFactory
import domain.vo.Currency
import domain.vo.Locale
import domain.vo.PlayerId
import domain.vo.SessionToken
import org.slf4j.LoggerFactory

/**
 * Mints + registers a session for the always-on shared lottery and opens an authenticated
 * WebSocket to the provider, handing the caller a duplex [LotterySocketSession]. Mirrors
 * [OpenSessionUsecase] but, since the lottery ships no game client, returns a live socket
 * instead of an iframe launch URL — the gRPC bridge relays its frames.
 */
class StreamLotteryUsecase(
    private val gameVariantRepository: IGameVariantRepository,
    private val sessionRepository: ISessionRepository,
    private val aggregatorFactory: IAggregatorFactory,
    private val eventPublisher: IEventPublisherPort,
) {

    private val logger = LoggerFactory.getLogger(StreamLotteryUsecase::class.java)

    suspend operator fun invoke(playerId: PlayerId, currency: Currency): Result<LotterySocketSession> = runCatching {
        // Resolved by the stable provider-side symbol ("lottery"), not the generated game identity
        // ("<provider>_lottery"). SessionFactory enforces game/provider/aggregator active below.
        val gameVariant = domainRequireNotNull(
            gameVariantRepository.findBySymbol(LOTTERY_SYMBOL)
        ) { GameNotFoundException() }

        val aggregator = gameVariant.game.provider.aggregator

        val session = SessionFactory.create(
            token = SessionToken(generateBase24Token()),
            playerId = playerId,
            gameVariant = gameVariant,
            currency = currency,
            locale = DEFAULT_LOCALE,
            platform = DEFAULT_PLATFORM,
        )

        // Commit the session BEFORE connecting: the provider drives a synchronous /player webhook
        // that resolves this session by token in a separate DB connection, so the row must already
        // be committed (same ordering reason documented in OpenSessionUsecase).
        val savedSession = sessionRepository.save(session)

        val socket = aggregatorFactory.createLotteryStreamAdapter(aggregator).connect(savedSession.token)

        // Socket is open — a broker failure must never fail the open at this point.
        try {
            eventPublisher.publish(SessionEvent(savedSession))
        } catch (e: Exception) {
            logger.error(
                "EVENT PUBLISH FAILED (event lost): route={} sessionId={} player={}",
                SessionEvent.route, savedSession.id, savedSession.playerId.value, e,
            )
        }

        logger.info("Lottery stream opened: id={} player={}", savedSession.id, savedSession.playerId.value)

        socket
    }.onFailure { e ->
        if (e !is DomainException) {
            logger.error("Failed to open lottery stream: player={}", playerId.value, e)
        }
    }

    private fun generateBase24Token(): String = buildString(TOKEN_LENGTH) {
        repeat(TOKEN_LENGTH) {
            append(BASE24_CHARS.random())
        }
    }

    companion object {
        private const val LOTTERY_SYMBOL = "lottery"

        private val DEFAULT_LOCALE = Locale("en")

        private val DEFAULT_PLATFORM = Platform.MOBILE

        private const val BASE24_CHARS = "BCDFGHJKMPQRTVWXY2346789"

        private const val TOKEN_LENGTH = 32
    }
}
