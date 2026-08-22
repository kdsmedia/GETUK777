package application.port.external

import domain.model.PlayerBalance
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId

/**
 * Port interface for wallet operations.
 * Implementations connect to external wallet service.
 */
interface IWalletPort {
    /**
     * Get player's current balance.
     */
    suspend fun findBalance(playerId: PlayerId, currency: Currency): PlayerBalance

    /**
     * All of the player's wallets across currencies. Empty when the player has no accounts yet.
     */
    suspend fun findBalances(playerId: PlayerId): List<PlayerBalance>

    /**
     * Withdraw funds from player's wallet.
     * Returns the updated balance after withdrawal.
     */
    suspend fun withdraw(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        realAmount: Amount,
        bonusAmount: Amount
    ): PlayerBalance

    /**
     * Deposit funds to player's wallet.
     * Returns the updated balance after deposit.
     */
    suspend fun deposit(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        realAmount: Amount,
        bonusAmount: Amount
    ): PlayerBalance
}
