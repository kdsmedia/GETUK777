package application.port.external

import kotlinx.coroutines.flow.Flow

/**
 * Driven port for the provider's public jackpot WebSocket. The adapter opens an
 * un-authenticated socket and exposes the active-draw frames as a cold [Flow] — it
 * connects on collection and closes on cancellation. casino-engine fans this single
 * upstream out to every gRPC subscriber (see `JackpotBroadcaster`).
 */
interface IJackpotStreamPort {

    fun stream(): Flow<JackpotState>
}

/** One active-jackpot snapshot parsed off the provider socket. */
data class JackpotState(

    val identity: String,

    val currency: String,

    val startAt: Long,

    val endAt: Long,

    val prizePool: Long,

    val status: String,
)
