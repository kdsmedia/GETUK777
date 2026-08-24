package infrastructure.aggregator.skyline.client

import infrastructure.aggregator.skyline.SkylineConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Outbound Skyline calls. Every action goes to the same partner URL and is told apart by the
 * `action` field; `apikey` rides along on all of them.
 *
 * The body is a bare JWT and so is the reply, which is verified before it is read — an unverified
 * reply is indistinguishable from one an attacker wrote. The vendor accepts unsigned JSON too, but
 * signing is what makes their answer trustworthy, so this client always signs.
 *
 * Every response is HTTP 200, including failures: an error is a `{"error": N, "description": ...}`
 * object sitting where the result would be, which is why success is decided by inspecting the
 * payload rather than the status line.
 */
class SkylineHttpClient(private val config: SkylineConfig) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val jwt = SkylineJwt(config.jwtSecret)

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }

    /** [params] carries the action's own fields; `apikey` and `action` are added here so no caller
     *  can forget them. Blank values are dropped — the vendor treats an empty optional as a value
     *  and echoes it into the launch URL. */
    suspend fun call(action: String, params: Map<String, JsonElement> = emptyMap()): JsonElement {
        check(config.apiUrl.isNotBlank()) { "Skyline api url not configured" }
        check(config.jwtSecret.isNotBlank()) { "Skyline jwt secret not configured" }

        val payload = buildJsonObject {
            put(FIELD_API_KEY, config.apiKey)
            put(FIELD_ACTION, action)
            params.forEach { (key, value) -> if (value.isPresent()) put(key, value) }
        }

        val response = client.post(config.apiUrl) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(jwt.encode(payload))
        }

        val body = response.bodyAsText()
        check(response.status.isSuccess()) { "Skyline $action failed with HTTP status ${response.status}: $body" }

        val decoded = jwt.decode(body)
            ?: error("Skyline $action answered a body that is not a token signed with our secret")

        val result = decoded[FIELD_RESULT] ?: error("Skyline $action answered without a result")

        // An error replaces the result rather than sitting beside it, so it is only visible here.
        (result as? JsonObject)?.get(FIELD_ERROR)?.jsonPrimitive?.intOrNull?.let { code ->
            val description = result[FIELD_DESCRIPTION]?.jsonPrimitive?.contentOrNull.orEmpty()
            error("Skyline $action failed with error $code: $description")
        }

        return result
    }

    suspend fun <T> call(action: String, params: Map<String, JsonElement>, serializer: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(serializer, call(action, params))

    /** A blank string is an absent optional, not an empty one. Numbers and booleans always count. */
    private fun JsonElement.isPresent(): Boolean =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.isNotBlank() ?: true

    private companion object {
        const val FIELD_API_KEY = "apikey"

        const val FIELD_ACTION = "action"

        const val FIELD_RESULT = "result"

        const val FIELD_ERROR = "error"

        const val FIELD_DESCRIPTION = "description"
    }
}
