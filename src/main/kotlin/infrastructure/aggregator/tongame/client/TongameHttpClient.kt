package infrastructure.aggregator.tongame.client

import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.dto.CreateSessionRequest
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
 * Operator → provider REST client (`/api/v1/operator/...`). Every call is authenticated with
 * the `X-Identity` + `X-Api-Key` headers. `expectSuccess` makes non-2xx responses throw so
 * failures (e.g. 409 on a duplicate session token) surface instead of being swallowed.
 */
class TongameHttpClient(private val config: TongameConfig) {

    private val client = HttpClient(CIO) {
        expectSuccess = true
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

    /** Registers our session token with the provider. The provider stores it and uses it to
     *  authenticate later wallet webhooks. Returns 204 (no body). */
    suspend fun createSession(sessionToken: String, playerId: String, gameId: String) {
        client.post("${config.apiUrl}/api/v1/operator/session") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(
                CreateSessionRequest(
                    sessionToken = sessionToken,
                    playerId = playerId,
                    gameId = gameId,
                )
            )
        }
    }

    private fun HttpRequestBuilder.auth() {
        header("X-Identity", config.operatorIdentity)
        header("X-Api-Key", config.apiKey)
    }
}
