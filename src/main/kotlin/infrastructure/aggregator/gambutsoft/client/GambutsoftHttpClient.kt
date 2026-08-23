package infrastructure.aggregator.gambutsoft.client

import infrastructure.aggregator.gambutsoft.GambutsoftConfig
import infrastructure.aggregator.gambutsoft.client.dto.GameCatalogItemDto
import infrastructure.aggregator.gambutsoft.client.dto.LoginResponseDto
import infrastructure.aggregator.gambutsoft.client.dto.OpenGameContentDto
import infrastructure.aggregator.gambutsoft.client.dto.OpenGameRequestDto
import infrastructure.aggregator.gambutsoft.client.dto.OpenGameResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Outbound Gambutsoft calls: login, catalogue, and session creation.
 *
 * `openGame` encodes its body by hand instead of delegating to ContentNegotiation, because the
 * signature covers the exact bytes sent — any re-encoding between signing and sending invalidates
 * it. Login and catalogue are unsigned; they authenticate with credentials and a Bearer token.
 *
 * The access token is never cached: every `/auth/login` invalidates the tokens minted before it,
 * so a shared cache across pods would have them cancelling each other. Only the catalogue sync
 * needs a token, it needs it once, and `openGame` needs none at all.
 */
class GambutsoftHttpClient(private val config: GambutsoftConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    private val signer = GambutsoftSigner(config.secretKey)

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun login(): LoginResponseDto {
        check(config.officeApiUrl.isNotBlank()) { "Gambutsoft office API url not configured" }

        // Form-encoded, not JSON: the vendor answers 400/401 to a JSON body here.
        val response = client.post("${config.officeApiUrl}$PATH_LOGIN") {
            contentType(ContentType.Application.FormUrlEncoded)
            accept(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, USER_AGENT)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append(FIELD_LOGIN, config.login)
                        append(FIELD_PASSWORD, config.password)
                    }
                )
            )
        }

        val body = response.bodyAsText()
        check(response.status.isSuccess()) { "Gambutsoft login failed with HTTP status ${response.status}: $body" }

        return json.decodeFromString(LoginResponseDto.serializer(), body)
    }

    /** The catalogue is per currency: a currency the account does not hold answers HTTP 400, and a
     *  currency it holds with no games enabled answers an empty array. */
    suspend fun getUserGames(accessToken: String, userId: String, currency: String): List<GameCatalogItemDto> {
        check(config.officeApiUrl.isNotBlank()) { "Gambutsoft office API url not configured" }

        val response = client.get("${config.officeApiUrl}$PATH_USERS/$userId$PATH_GAMES/$currency") {
            accept(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.Authorization, "$BEARER_PREFIX$accessToken")
        }

        val body = response.bodyAsText()
        check(response.status.isSuccess()) {
            "Gambutsoft getUserGames($currency) failed with HTTP status ${response.status}: $body"
        }

        return json.decodeFromString(ListSerializer(GameCatalogItemDto.serializer()), body)
    }

    suspend fun openGame(request: OpenGameRequestDto): OpenGameContentDto {
        check(config.clientApiUrl.isNotBlank()) { "Gambutsoft client API url not configured" }

        val body = json.encodeToString(OpenGameRequestDto.serializer(), request)

        val response = client.post("${config.clientApiUrl}$PATH_OPEN_GAME") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HEADER_SIGNATURE, signer.sign(body))
            setBody(body)
        }

        val text = response.bodyAsText()
        check(response.status.isSuccess()) {
            "Gambutsoft openGame failed with HTTP status ${response.status}: $text"
        }

        val decoded = json.decodeFromString(OpenGameResponseDto.serializer(), text)
        check(decoded.status == STATUS_SUCCESS) { "Gambutsoft openGame failed: ${decoded.error}" }
        check(decoded.content.game.url.isNotBlank()) { "Gambutsoft openGame returned no game url" }

        return decoded.content
    }

    private companion object {
        const val PATH_LOGIN = "/auth/login"

        const val PATH_USERS = "/users"

        const val PATH_GAMES = "/getUserGames"

        const val PATH_OPEN_GAME = "/games/openGame"

        const val FIELD_LOGIN = "login"

        const val FIELD_PASSWORD = "password"

        const val HEADER_SIGNATURE = "X-Signature"

        const val BEARER_PREFIX = "Bearer "

        const val STATUS_SUCCESS = "success"

        /** Their hosts sit behind a Cloudflare managed challenge that answers an HTML "Just a
         *  moment..." page — HTTP 403, not JSON — to clients it does not recognise. This value is
         *  one it lets through; sending it explicitly keeps the integration independent of whatever
         *  default the HTTP engine happens to carry. */
        const val USER_AGENT = "Ktor client"
    }
}
