package infrastructure.aggregator.skyline.webhook.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `session` is the token WE minted at launch and they echoed back; it resolves the session. */
@Serializable
data class SkylineGetBalanceRequest(
    val session: String,
)

/**
 * A bet, a win, or a refund of a previous one.
 *
 * The same [transaction] identifies all three: the vendor retries a call up to three times with it
 * unchanged and, if we never answer, resends it five minutes later with [isRefund] set. Nothing
 * here mints a new id, so idempotency and reversal both key off this one value.
 *
 * Today's game sends the bet and the win as separate calls, but the vendor states that future games
 * will carry both in one, so both legs are handled together.
 */
@Serializable
data class SkylineUpdateBalanceRequest(
    val session: String,

    val transaction: String,

    val round: String? = null,

    @SerialName("bet_amount") val betAmount: Long = 0,

    @SerialName("win_amount") val winAmount: Long = 0,

    /** False while free spins are still to come; true closes the round. */
    @SerialName("is_last") val isLast: Boolean = false,

    @SerialName("is_refund") val isRefund: Boolean = false,

    /** The free-round grant this call belongs to — our own reference id, echoed back. */
    val bonus: String? = null,
)
