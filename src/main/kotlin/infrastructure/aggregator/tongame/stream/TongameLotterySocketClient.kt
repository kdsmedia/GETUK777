package infrastructure.aggregator.tongame.stream

import application.port.external.ILotteryStreamPort
import application.port.external.LotterySocketSession
import domain.vo.SessionToken
import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.TongameHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Opens an authenticated WebSocket to the provider's `/ws/lottery`. We first register our own
 * session token via the REST `/api/v1/session` (the provider mints nothing), then connect the
 * socket and send the `auth` frame so the provider resolves our session by `(token, operator)`.
 * Frames are relayed as raw text — the lottery protocol lives only in the provider.
 */
class TongameLotterySocketClient(
    private val config: TongameConfig,
) : ILotteryStreamPort {

    private val httpClient = TongameHttpClient(config)

    override suspend fun connect(token: SessionToken): LotterySocketSession {
        // Register our session token so the socket `auth` resolves it (provider calls /player back).
        httpClient.createSession(token.value)

        val client = HttpClient(CIO) {
            install(WebSockets)
        }

        val session = client.webSocketSession("${config.wsUrl}$LOTTERY_PATH")
        session.send(Frame.Text(authFrame(token)))

        return KtorLotterySocketSession(client, session)
    }

    private fun authFrame(token: SessionToken): String =
        Json.encodeToString(AuthFrame(token = token.value, operator = config.operatorIdentity))

    @Serializable
    private data class AuthFrame(

        val type: String = "auth",

        val token: String,

        val operator: String,
    )

    companion object {
        private const val LOTTERY_PATH = "/ws/lottery"
    }
}

private class KtorLotterySocketSession(
    private val client: HttpClient,
    private val session: DefaultClientWebSocketSession,
) : LotterySocketSession {

    override val incoming: Flow<String> = flow {
        for (frame in session.incoming) {
            if (frame is Frame.Text) emit(frame.readText())
        }
    }

    override suspend fun send(text: String) {
        session.send(Frame.Text(text))
    }

    override suspend fun close() {
        runCatching { session.close() }
        client.close()
    }
}
