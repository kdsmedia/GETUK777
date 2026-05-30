package infrastructure.aggregator.tongame.client

import infrastructure.aggregator.tongame.TongameConfig
import infrastructure.aggregator.tongame.client.dto.CreateSessionRequest
import infrastructure.aggregator.tongame.client.dto.GameDto
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
 * Operator → provider REST client (`/api/v1/...`). Every call is authenticated with the
 * `X-Operator` + `X-Secret-Key` headers. `expectSuccess` makes non-2xx responses throw so
 * failures surface instead of being swallowed.
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
        return client.get("${config.apiUrl}/api/v1/games") {
            auth()
        }.body()
    }

    /** Registers our own session [token] with the provider (it mints nothing). The provider then
     *  calls our `/player` webhook with this same [token] to learn the player, and echoes the token
     *  back as `sessionToken` in every later wallet webhook. Throws on non-2xx (expectSuccess). */
    suspend fun createSession(token: String) {
        client.post("${config.apiUrl}/api/v1/session") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(token = token))
        }
    }

    private fun HttpRequestBuilder.auth() {
        header("X-Operator", config.operatorIdentity)
        header("X-Secret-Key", config.apiKey)
    }
}
