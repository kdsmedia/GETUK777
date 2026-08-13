package infrastructure.aggregator.gamingflow.webhook.dto

import kotlinx.serialization.Serializable

/**
 * Seamless API v2 payloads (provider → operator).
 *
 * `sessionAlternativeId` is the session token we handed the provider as `AlternativeId` when the
 * session was created — it is how each call resolves back to our session. Amounts are integers in
 * the currency's minor units.
 */
@Serializable
data class GetBalanceParams(
    val callerId: Long? = null,

    val playerName: String = "",

    val currency: String = "",

    val gameId: String? = null,

    val bonusId: String? = null,

    val sessionId: String? = null,

    val sessionAlternativeId: String? = null,
)

@Serializable
data class WithdrawAndDepositParams(
    val callerId: Long? = null,

    val playerName: String = "",

    val withdraw: Long = 0,

    val deposit: Long = 0,

    val currency: String = "",

    val transactionRef: String = "",

    val gameRoundRef: String? = null,

    val gameId: String? = null,

    val bonusId: String? = null,

    val sessionId: String? = null,

    val sessionAlternativeId: String? = null,

    /** `GAME_PLAY` keeps the round open; `GAME_PLAY_FINAL` closes it. */
    val reason: String? = null,

    val chargeFreerounds: Int? = null,
)

@Serializable
data class RollbackTransactionParams(
    val callerId: Long? = null,

    val playerName: String = "",

    val transactionRef: String = "",

    val gameRoundRef: String? = null,

    val gameId: String? = null,

    val bonusId: String? = null,

    val sessionId: String? = null,

    val sessionAlternativeId: String? = null,
)

@Serializable
data class BalanceResult(
    val balance: Long,

    /** Omitted when the player holds no free rounds on this game — the field is optional and a
     *  zero would read as "a bonus exists and is spent". */
    val freeroundsLeft: Int? = null,
)

@Serializable
data class TransactionResult(
    val newBalance: Long,

    val transactionId: String,

    val freeroundsLeft: Int? = null,

    /** Tells the provider the round was played off the bonus, which is what makes it echo
     *  `freeround: true` back on that round. */
    val freeroundWasCharged: Boolean? = null,
)
