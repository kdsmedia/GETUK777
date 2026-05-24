package infrastructure.aggregator.tongame.client

import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.dto.CreateSessionRequest
import infrastructure.aggregator.tongame.client.dto.CreateSessionResponse
import infrastructure.aggregator.tongame.client.dto.GameDto
import infrastructure.aggregator.tongame.client.dto.ListGamesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Operator → provider REST client (slot v1, `/api/v1/operator/...`). Every call is
 * authenticated with the `X-Identity` + `X-Api-Key` headers.
 */
class TongameHttpClient(private val config: TongameConfig) {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun getGames(): List<GameDto> {
        return client.get("${config.apiUrl}/api/v1/operator/game") {
            auth()
        }.body<ListGamesResponse>().games
    }

    suspend fun createSession(playerId: String, gameId: String, currency: String): CreateSessionResponse {
        return client.post("${config.apiUrl}/api/v1/operator/session") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                CreateSessionRequest(
                    identity = config.operatorIdentity,
                    playerId = playerId,
                    gameId = gameId,
                    currency = currency,
                )
            )
        }.body()
    }

    private fun HttpRequestBuilder.auth() {
        header("X-Identity", config.operatorIdentity)
        header("X-Api-Key", config.apiKey)
    }
}
