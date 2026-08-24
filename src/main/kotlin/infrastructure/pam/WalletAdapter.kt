package infrastructure.pam

import application.port.external.IWalletPort
import com.nekgambling.pam.v1.EnsureAccountRequest
import com.nekgambling.pam.v1.FindAccountRequest
import com.nekgambling.pam.v1.TransactRequest
import com.nekgambling.pam.v1.WalletAccount
import com.nekgambling.pam.v1.WalletServiceGrpc
import domain.model.PlayerBalance
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.PlayerId
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The wallet ledger, inside pam-engine.
 *
 * **Money moves on (player, currency) — there is no account id anywhere.** A movement is one call:
 * pam resolves the purse from the pair and mints it on the player's first movement in a currency, so
 * nothing is looked up, nothing is cached and nothing has to be created first.
 *
 * **One signed `Transact` replaces Deposit/Withdraw.** A withdrawal is the same call with negative
 * amounts, and it is idempotent by `reference` — a retry of the same reference moves nothing and
 * answers the balance the first call produced.
 */
class WalletAdapter(
    channel: ManagedChannel
) : IWalletPort {

    private companion object {
        /** The ledger category every casino and sportbook movement has always been recorded under. */
        const val TYPE = "SPIN"
    }

    private val stub: WalletServiceGrpc.WalletServiceBlockingStub =
        WalletServiceGrpc.newBlockingStub(channel)

    override suspend fun findBalance(playerId: PlayerId, currency: Currency): PlayerBalance =
        account(playerId, currency).toPlayerBalance(currency)

    override suspend fun withdraw(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        realAmount: Amount,
        bonusAmount: Amount
    ): PlayerBalance = transact(playerId, transactionId, currency, -realAmount.value, -bonusAmount.value)

    override suspend fun deposit(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        realAmount: Amount,
        bonusAmount: Amount
    ): PlayerBalance = transact(playerId, transactionId, currency, realAmount.value, bonusAmount.value)

    private suspend fun transact(
        playerId: PlayerId,
        reference: String,
        currency: Currency,
        realAmount: Long,
        bonusAmount: Long,
    ): PlayerBalance {
        // Transact refuses a movement of nothing, and a zero move is exactly what a losing settle
        // is: report the balance the caller would have read anyway instead of failing the spin.
        if (realAmount == 0L && bonusAmount == 0L) {
            return findBalance(playerId, currency)
        }

        val request = TransactRequest.newBuilder()
            .setUserId(playerId.value.toLong())
            .setCurrency(currency.value)
            .setReference(reference)
            .setType(TYPE)
            .setRealAmount(realAmount)
            .setBonusAmount(bonusAmount)
            .build()

        val response = withContext(Dispatchers.IO) { stub.transact(request) }

        return response.account.toPlayerBalance(currency)
    }

    /**
     * The purse behind a balance read. Unlike a movement this still has to exist, because there is
     * nothing to report for a currency the player has never held — so a miss mints it and reads again.
     */
    private suspend fun account(playerId: PlayerId, currency: Currency): WalletAccount {
        val request = FindAccountRequest.newBuilder()
            .setUserId(playerId.value.toLong())
            .setCurrency(currency.value)
            .build()

        return try {
            withContext(Dispatchers.IO) { stub.findAccount(request) }
        } catch (e: StatusRuntimeException) {
            if (e.status.code != Status.Code.NOT_FOUND) throw e
            // EnsureAccount is idempotent, so two racing reads both end up with the same purse.
            withContext(Dispatchers.IO) {
                stub.ensureAccount(
                    EnsureAccountRequest.newBuilder()
                        .setUserId(playerId.value.toLong())
                        .setCurrency(currency.value)
                        .build()
                )
                stub.findAccount(request)
            }
        }
    }

    private fun WalletAccount.toPlayerBalance(currency: Currency): PlayerBalance = PlayerBalance(
        realAmount = Amount(realBalance),
        bonusAmount = Amount(bonusBalance),
        currency = currency,
    )
}
