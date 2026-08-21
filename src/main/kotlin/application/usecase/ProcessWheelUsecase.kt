package application.usecase

import application.port.external.IWalletPort
import application.port.external.IWebhookGuardPort
import domain.exception.notfound.TransactionNotFoundException
import domain.model.SportbookSession
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId
import org.slf4j.LoggerFactory

/**
 * Fortune Wheel money flow — pure wallet passthrough, deliberately outside the [Bet] model.
 *
 * The wheel has no aggregate of its own, so replay/ordering safety comes from
 * [IWebhookGuardPort]: a stake claims its transaction nonce (a retry finds it claimed and
 * does nothing — the wallet also dedups by transaction id), and a rollback marks the
 * transaction rolled back FIRST, so a stake arriving after its own rollback is refused.
 * A rollback for a transaction that never staked answers [TransactionNotFoundException]
 * instead of minting money.
 */
class ProcessWheelUsecase(
    private val walletPort: IWalletPort,
    private val guardPort: IWebhookGuardPort,
) {

    private val logger = LoggerFactory.getLogger(ProcessWheelUsecase::class.java)

    /** Debits the stake when the wheel is spun. */
    suspend fun credit(
        session: SportbookSession,
        transactionId: String,
        currency: Currency,
        amount: Amount,
    ): Result<Unit> = runCatching {
        val key = txKey(transactionId)

        if (guardPort.isRolledBack(key)) {
            logger.info("Wheel credit refused, transaction already rolled back: tx={}", transactionId)
            return@runCatching
        }

        if (!guardPort.claimNonce(key, GUARD_TTL_SECONDS)) return@runCatching // retry — already processed

        walletPort.withdraw(session.playerId, "sportbook:wheel:credit:$transactionId", currency, amount, Amount.ZERO)
    }

    /** Credits the win when the spin outcome is determined. */
    suspend fun payout(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        amount: Amount,
    ): Result<Unit> = runCatching {
        walletPort.deposit(playerId, "sportbook:wheel:payout:$transactionId", currency, amount, Amount.ZERO)
    }

    /** Cancels a stake: refunds it, or — when the stake never arrived — poisons the transaction. */
    suspend fun rollback(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        amount: Amount,
    ): Result<Unit> = runCatching {
        val key = txKey(transactionId)

        if (guardPort.isRolledBack(key)) return@runCatching // idempotent retry

        val neverCredited = guardPort.claimNonce(key, GUARD_TTL_SECONDS)

        guardPort.markRolledBack(key, GUARD_TTL_SECONDS)

        if (neverCredited) {
            // Rollback arrived before the stake — the claim above makes the late stake a no-op.
            throw TransactionNotFoundException()
        }

        walletPort.deposit(playerId, "sportbook:wheel:rollback:$transactionId", currency, amount, Amount.ZERO)
    }

    private fun txKey(transactionId: String) = "sportbook:wheel:tx:$transactionId"

    private companion object {
        const val GUARD_TTL_SECONDS = 86_400L
    }
}
