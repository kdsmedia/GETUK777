package infrastructure.aggregator.gamingflow.client

import infrastructure.aggregator.gamingflow.GamingFlowConfig
import infrastructure.aggregator.gamingflow.client.dto.GameDto
import infrastructure.aggregator.gamingflow.client.dto.GameListResultDto
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcRequest
import infrastructure.aggregator.gamingflow.client.dto.GamingFlowRpcResponse
import infrastructure.aggregator.gamingflow.client.dto.SessionResultDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Operator API v1 client — every method is a JSON-RPC 2.0 call against the one signed endpoint.
 *
 * The request body is serialized once and signed byte-for-byte, so this client encodes JSON by hand
 * instead of delegating to ContentNegotiation: any re-encoding between signing and sending would
 * invalidate the signature.
 */
class GamingFlowHttpClient(private val config: GamingFlowConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val signer = GamingFlowSigner(keyId = config.keyId, keyValue = config.keyValue)

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    /** Passing [bankGroupId] makes the provider attach per-group `Settings` to every game. */
    suspend fun listGames(bankGroupId: String? = null): List<GameDto> {
        val params = buildJsonObject {
            bankGroupId?.let { put("BankGroupId", it) }
        }

        return decode(GameListResultDto.serializer(), invoke("Game.List", params)).games
    }

    /** Upsert. Currency is honoured on create and ignored on update — a bank group's currency is
     *  immutable once set. */
    suspend fun setBankGroup(id: String, currency: String) {
        invoke(
            method = "BankGroup.Set",
            params = buildJsonObject {
                put("Id", id)
                put("Currency", currency)
            }
        )
    }

    /** Upsert. `BankGroupId` binds on create and is ignored on update — a player never changes
     *  bank group, and therefore never changes currency. */
    suspend fun setPlayer(id: String, bankGroupId: String, nick: String? = null) {
        invoke(
            method = "Player.Set",
            params = buildJsonObject {
                put("Id", id)
                put("BankGroupId", bankGroupId)
                nick?.let { put("Nick", it) }
            }
        )
    }

    /** Registers a bonus id the provider will accept as `BonusId` on session creation. The
     *  free-round balance itself stays on our side and is served through `getBalance`. */
    suspend fun setBonus(id: String) {
        invoke(
            method = "Bonus.Set",
            params = buildJsonObject {
                put("Id", id)
            }
        )
    }

    suspend fun createSession(
        playerId: String,
        gameId: String,
        alternativeId: String,
        language: String,
        bonusId: String? = null,
    ): SessionResultDto {
        val params = buildJsonObject {
            put("PlayerId", playerId)
            put("GameId", gameId)
            put("AlternativeId", alternativeId)
            put("RestorePolicy", config.restorePolicy)
            bonusId?.let { put("BonusId", it) }
            config.baseHost.takeIf { it.isNotBlank() }?.let { put("BaseHost", it) }
            config.staticHost.takeIf { it.isNotBlank() }?.let { put("StaticHost", it) }
            put("Params", buildJsonObject { put("language", language) })
        }

        return decode(SessionResultDto.serializer(), invoke("Session.Create", params))
    }

    suspend fun createDemoSession(
        gameId: String,
        bankGroupId: String,
        language: String,
    ): SessionResultDto {
        val params = buildJsonObject {
            put("GameId", gameId)
            put("BankGroupId", bankGroupId)
            put("StartBalance", config.demoStartBalance)
            config.baseHost.takeIf { it.isNotBlank() }?.let { put("BaseHost", it) }
            config.staticHost.takeIf { it.isNotBlank() }?.let { put("StaticHost", it) }
            put("Params", buildJsonObject { put("language", language) })
        }

        return decode(SessionResultDto.serializer(), invoke("Session.CreateDemo", params))
    }

    /** Closes a session ahead of time. Only needed when back-office statistics must settle
     *  immediately — otherwise each new session implicitly closes the previous one. */
    suspend fun closeSession(sessionId: String) {
        invoke(
            method = "Session.Close",
            params = buildJsonObject {
                put("SessionId", sessionId)
            }
        )
    }

    private suspend fun invoke(method: String, params: JsonObject): JsonElement {
        check(config.apiUrl.isNotBlank()) { "GamingFlow API url not configured" }

        val body = json.encodeToString(
            GamingFlowRpcRequest.serializer(),
            GamingFlowRpcRequest(method = method, id = nextRequestId(), params = params)
        )

        val nonce = signer.nextNonce()
        val timestamp = System.currentTimeMillis() / MILLIS_PER_SECOND

        val response = client.post(config.apiUrl) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            header(HEADER_NONCE, nonce)
            header(HEADER_TIMESTAMP, timestamp.toString())
            header(HEADER_SUBJECT, config.subject)
            header(HEADER_SIGNATURE, signer.sign(body, nonce, timestamp))
            setBody(body)
        }

        check(response.status.isSuccess()) {
            "GamingFlow $method failed with HTTP status ${response.status}"
        }

        val rpc = json.decodeFromString(GamingFlowRpcResponse.serializer(), response.bodyAsText())

        rpc.error?.let { error("GamingFlow $method failed: ${it.code} ${it.message}") }

        // A method with no response parameters answers `"result": {}`, never a missing result.
        return rpc.result ?: error("GamingFlow $method returned neither result nor error")
    }

    private fun <T> decode(serializer: DeserializationStrategy<T>, element: JsonElement): T =
        json.decodeFromJsonElement(serializer, element)

    private fun nextRequestId(): Long = Random.nextInt(1, Int.MAX_VALUE).toLong()

    private companion object {
        const val HEADER_NONCE = "X-Nonce"

        const val HEADER_SIGNATURE = "X-Signature"

        const val HEADER_SUBJECT = "X-Subject"

        const val HEADER_TIMESTAMP = "X-Timestamp"

        const val MILLIS_PER_SECOND = 1_000L
    }
}
