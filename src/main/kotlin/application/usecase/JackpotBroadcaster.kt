package application.usecase

import application.port.external.JackpotState
import application.port.factory.IAggregatorFactory
import domain.exception.domainRequireNotNull
import domain.exception.notfound.GameNotFoundException
import domain.repository.IGameVariantRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory

/**
 * Bridges the provider's single public jackpot socket to many gRPC subscribers. The upstream
 * is resolved from the active lottery's aggregator and shared with `replay = 1`, so each new
 * gRPC client immediately gets the latest frame. `WhileSubscribed` keeps the upstream socket
 * open only while at least one client is connected; a drop/close is reconnected with a fixed
 * backoff. The frames themselves are opaque to everyone but the provider.
 */
class JackpotBroadcaster(
    private val gameVariantRepository: IGameVariantRepository,
    private val aggregatorFactory: IAggregatorFactory,
) {

    private val logger = LoggerFactory.getLogger(JackpotBroadcaster::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val shared: Flow<JackpotState> = channelFlow {
        while (isActive) {
            try {
                connectUpstream().collect { send(it) }
            } catch (e: Exception) {
                logger.warn("jackpot upstream failed; reconnecting in {}ms", RECONNECT_DELAY_MS, e)
            }
            delay(RECONNECT_DELAY_MS)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    fun stream(): Flow<JackpotState> = shared

    private suspend fun connectUpstream(): Flow<JackpotState> {
        // Resolved by the stable provider-side symbol ("lottery"), not the generated game identity.
        val gameVariant = domainRequireNotNull(
            gameVariantRepository.findBySymbol(LOTTERY_SYMBOL)
        ) { GameNotFoundException() }

        val aggregator = gameVariant.game.provider.aggregator
        return aggregatorFactory.createJackpotStreamAdapter(aggregator).stream()
    }

    companion object {
        private const val LOTTERY_SYMBOL = "lottery"

        private const val RECONNECT_DELAY_MS = 3_000L

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
