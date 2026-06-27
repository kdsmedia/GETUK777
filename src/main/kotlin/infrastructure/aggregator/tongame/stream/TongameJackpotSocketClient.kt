package infrastructure.aggregator.tongame.stream

import application.port.external.IJackpotStreamPort
import application.port.external.JackpotState
import infrastructure.aggregator.tongame.TongameConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Opens the provider's PUBLIC `/ws/jackpot` (no session, no auth) and emits each active-draw
 * frame as a [JackpotState]. Cold: the socket opens when collected and closes when the flow is
 * cancelled. The provider pushes the current frame on connect and a fresh frame on every change.
 */
class TongameJackpotSocketClient(
    private val config: TongameConfig,
) : IJackpotStreamPort {

    override fun stream(): Flow<JackpotState> = flow {
        val client = HttpClient(CIO) { install(WebSockets) }
        val session = client.webSocketSession("${config.wsUrl}$JACKPOT_PATH")
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) parse(frame.readText())?.let { emit(it) }
            }
        } finally {
            runCatching { session.close() }
            client.close()
        }
    }

    private fun parse(text: String): JackpotState? {
        val obj = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        fun str(key: String) = obj[key]?.jsonPrimitive?.contentOrNull
        fun long(key: String) = obj[key]?.jsonPrimitive?.longOrNull
        val currency = str("currency") ?: return null
        return JackpotState(
            identity = str("identity") ?: "lottery",
            currency = currency,
            startAt = long("startAt") ?: 0L,
            endAt = long("endAt") ?: 0L,
            prizePool = long("prizePool") ?: 0L,
            status = str("status") ?: "",
        )
    }

    companion object {
        private const val JACKPOT_PATH = "/ws/jackpot"
    }
}
