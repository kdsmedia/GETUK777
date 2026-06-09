package application.usecase

import application.port.factory.IAggregatorFactory
import domain.event.AppEventPublisher
import domain.event.SessionEvent
import domain.exception.DomainException
import domain.model.Session
import domain.repository.ISessionRepository
import org.slf4j.LoggerFactory

class OpenSessionUsecase(
    private val aggregatorFactory: IAggregatorFactory,
    private val sessionRepository: ISessionRepository,
    private val eventPublisher: AppEventPublisher,
) {

    private val logger = LoggerFactory.getLogger(OpenSessionUsecase::class.java)

    suspend operator fun invoke(session: Session, lobbyUrl: String): Result<Response> = runCatching {
        val aggregator = session.gameVariant.game.provider.aggregator

        logger.info(
            "Opening session: player={} game={} aggregator={}",
            session.playerId.value, session.gameVariant.game.identity.value, aggregator.identity.value,
        )

        val gameAdapter = aggregatorFactory.createGameAdapter(aggregator)

        // Persist (and commit) the session BEFORE launching the provider. getLaunchUrl can drive a
        // synchronous provider -> operator webhook that resolves this session by token (e.g. TONGame
        // mints its session via POST /api/v1/session, which calls our /player back with the same
        // token). That callback reads in a separate DB connection, so the row must already be
        // committed; saving after the launch call left it invisible and the provider answered 401
        // UNKNOWN_SESSION, surfacing here as a 500.
        val updatedSession = sessionRepository.save(session)

        val launchUrl = gameAdapter.getLaunchUrl(updatedSession, lobbyUrl)

        eventPublisher.publish(SessionEvent(updatedSession))

        logger.info("Session opened: id={} player={}", updatedSession.id, updatedSession.playerId.value)

        Response(session = updatedSession, launchUrl = launchUrl)
    }.onFailure { e ->
        if (e !is DomainException) {
            logger.error(
                "Failed to open session: player={} game={}",
                session.playerId.value, session.gameVariant.game.identity.value, e,
            )
        }
    }

    data class Response(val session: Session, val launchUrl: String)
}
