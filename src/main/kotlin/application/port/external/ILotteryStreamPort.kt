package application.port.external

import domain.vo.SessionToken
import kotlinx.coroutines.flow.Flow

/**
 * Driven port for the provider's lottery WebSocket. The adapter registers the session token,
 * opens an authenticated socket, and exposes it as a raw-text duplex channel. casino-engine
 * never interprets the frames — they are relayed opaquely to/from the gRPC client.
 */
interface ILotteryStreamPort {

    suspend fun connect(token: SessionToken): LotterySocketSession
}

interface LotterySocketSession {

    val incoming: Flow<String>

    suspend fun send(text: String)

    suspend fun close()
}
