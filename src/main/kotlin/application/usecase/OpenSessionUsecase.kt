package application.usecase

import application.port.external.IEventPublisherPort
import application.port.factory.IAggregatorFactory
import domain.event.SessionEvent
import domain.exception.DomainException
import domain.model.Session
import domain.repository.IFreespinRepository
import domain.repository.ISessionRepository
import domain.util.ext.InstantExt
import org.slf4j.LoggerFactory

class OpenSessionUsecase(
    private val aggregatorFactory: IAggregatorFactory,
    private val sessionRepository: ISessionRepository,
    private val freespinRepository: IFreespinRepository,
    private val eventPublisher: IEventPublisherPort,
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
        val savedSession = sessionRepository.save(session)

        // Resolved here rather than asked for by the caller: whether the next round is free is a
        // fact about the player's grants, not something the lobby should have to know and pass in.
        val freespin = freespinRepository.findRedeemable(
            playerId = session.playerId,
            gameVariantId = session.gameVariant.id,
            now = InstantExt.now(),
        )

        if (freespin != null) {
            logger.info(
                "Session opens on freespin: reference={} left={}",
                freespin.referenceId.value, freespin.remainingCount,
            )
        }

        val launch = gameAdapter.getLaunchUrl(savedSession, lobbyUrl, freespin)

        // Providers that mint their own session id report it back here. Persisting it lets an
        // inbound webhook resolve the session by the provider's identifier as well as by ours.
        val updatedSession = if (launch.externalToken != null && launch.externalToken != savedSession.externalToken) {
            sessionRepository.save(savedSession.copy(externalToken = launch.externalToken))
        } else {
            savedSession
        }

        // The session is committed and the launch URL issued — a broker failure must never
        // fail the open at this point.
        try {
            eventPublisher.publish(SessionEvent(updatedSession))
        } catch (e: Exception) {
            logger.error(
                "EVENT PUBLISH FAILED (event lost): route={} sessionId={} player={}",
                SessionEvent.route, updatedSession.id, updatedSession.playerId.value, e,
            )
        }

        logger.info("Session opened: id={} player={}", updatedSession.id, updatedSession.playerId.value)

        Response(session = updatedSession, launchUrl = launch.url)
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
