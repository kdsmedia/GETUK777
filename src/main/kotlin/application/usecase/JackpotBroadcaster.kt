package application.usecase

import application.port.external.JackpotState
import application.port.factory.IAggregatorFactory
import domain.exception.domainRequireNotNull
import domain.exception.notfound.AggregatorNotFoundException
import domain.repository.IAggregatorRepository
import domain.vo.Identity
import java.util.concurrent.ConcurrentHashMap
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
 * Bridges each provider's public jackpot socket to many gRPC subscribers — UNIVERSAL and
 * resolved PER AGGREGATOR. For a requested aggregator identity it resolves that aggregator and
 * opens its [application.port.factory.IAggregatorFactory.createJackpotStreamAdapter]; the upstream
 * is shared with `replay = 1` (one socket per aggregator, kept open only `WhileSubscribed`, with a
 * fixed reconnect backoff), so different providers' jackpots stream independently and each new
 * client gets the latest frame immediately. The frames are opaque to everyone but the provider.
 */
class JackpotBroadcaster(
    private val aggregatorRepository: IAggregatorRepository,
    private val aggregatorFactory: IAggregatorFactory,
) {

    private val logger = LoggerFactory.getLogger(JackpotBroadcaster::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One shared, reconnecting upstream flow per aggregator identity. */
    private val streams = ConcurrentHashMap<String, Flow<JackpotState>>()

    fun stream(aggregator: String): Flow<JackpotState> =
        streams.computeIfAbsent(aggregator) { id -> build(id) }

    private fun build(aggregator: String): Flow<JackpotState> = channelFlow {
        while (isActive) {
            try {
                connectUpstream(aggregator).collect { send(it) }
            } catch (e: Exception) {
                logger.warn("jackpot upstream for aggregator '{}' failed; reconnecting in {}ms", aggregator, RECONNECT_DELAY_MS, e)
            }
            delay(RECONNECT_DELAY_MS)
        }
    }.shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    private suspend fun connectUpstream(aggregator: String): Flow<JackpotState> {
        val resolved = domainRequireNotNull(
            aggregatorRepository.findByIdentity(Identity(aggregator))
        ) { AggregatorNotFoundException() }

        return aggregatorFactory.createJackpotStreamAdapter(resolved).stream()
    }

    companion object {
        private const val RECONNECT_DELAY_MS = 3_000L

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
