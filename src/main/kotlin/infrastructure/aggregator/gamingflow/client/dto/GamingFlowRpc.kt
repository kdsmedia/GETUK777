package infrastructure.aggregator.gamingflow.client.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

const val JSON_RPC_VERSION = "2.0"

/**
 * JSON-RPC 2.0 request envelope. Always a Request (never a Notification) — the provider requires
 * [id] on every call. [params] stays a raw [JsonObject] because the body is serialized once and
 * signed byte-for-byte; re-encoding it anywhere else would break the signature.
 */
@Serializable
data class GamingFlowRpcRequest(
    val jsonrpc: String = JSON_RPC_VERSION,

    val method: String,

    val id: Long,

    val params: JsonObject,
)

@Serializable
data class GamingFlowRpcResponse(
    val jsonrpc: String = JSON_RPC_VERSION,

    val id: Long? = null,

    val result: JsonElement? = null,

    val error: GamingFlowRpcError? = null,
)

@Serializable
data class GamingFlowRpcError(
    val code: Int,

    val message: String = "",
)
