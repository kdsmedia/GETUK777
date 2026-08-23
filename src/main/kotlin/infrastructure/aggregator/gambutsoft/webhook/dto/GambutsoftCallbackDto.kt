package infrastructure.aggregator.gambutsoft.webhook.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class GetBalanceRequest(
    val login: String = "",

    val sessionid: String,
)

/**
 * A bet, a win, or both in one call. `bet` and `win` are read as raw JSON primitives because some
 * of the vendor's sub-providers send them as strings and the rest as numbers.
 */
@Serializable
data class WriteBetRequest(
    val bet: JsonPrimitive? = null,

    val win: JsonPrimitive? = null,

    val login: String = "",

    val sessionid: String,

    val transactionId: String,

    /** Optional on the wire — only some sub-providers group transactions into a round. */
    val roundId: String? = null,

    @SerialName("round_finished") val roundFinished: Boolean = false,
)

/** `transactionId` is the id of the bet being reversed, not a new one. */
@Serializable
data class RollbackRequest(
    val login: String = "",

    val sessionid: String,

    val transactionId: String,
)

/**
 * The one response shape all three callbacks share. `balance` is a JSON number on their side, so it
 * is carried as an unquoted primitive rather than a string.
 */
@Serializable
data class CallbackResponse(
    val balance: JsonPrimitive,

    val currency: String,

    val error: String,

    val login: String,

    val status: String,
)
