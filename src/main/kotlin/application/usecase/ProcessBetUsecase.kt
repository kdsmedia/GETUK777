package application.usecase

import application.command.bet.SettleBetResult
import application.port.external.IEventPublisherPort
import application.port.external.IWalletPort
import domain.event.BetEvent
import domain.exception.badrequest.BetCurrencyMismatchException
import domain.exception.domainRequire
import domain.exception.domainRequireNotNull
import domain.exception.notfound.BetNotFoundException
import domain.model.Bet
import domain.model.BetSelection
import domain.model.BetStatus
import domain.model.BetType
import domain.model.SportbookSession
import domain.repository.IBetRepository
import domain.util.ext.InstantExt
import domain.vo.Amount
import domain.vo.Currency
import domain.vo.ExternalBetId
import domain.vo.PlayerId
import org.slf4j.LoggerFactory

/**
 * Sportbook bet lifecycle — the sport counterpart of [ProcessSpinUsecase].
 *
 * A bet is born on the aggregator's hold request ([place], keyed by the placement transaction id),
 * enriched with the real bet id + selections on confirmation ([confirm]), settled or re-settled
 * any number of times ([settle], absolute overwrite of `status`/`winAmount`), and erased when the
 * placement is rolled back ([rollback]). Wallet moves are synchronous — a hold that cannot be
 * covered must decline the placement. Every surviving state change publishes [BetEvent] AFTER
 * the write commits.
 */
class ProcessBetUsecase(
    private val betRepository: IBetRepository,
    private val walletPort: IWalletPort,
    private val eventPublisher: IEventPublisherPort,
) {

    private val logger = LoggerFactory.getLogger(ProcessBetUsecase::class.java)

    /** Holds the stake and creates the bet in OPEN. Retries with the same transaction id are no-ops. */
    suspend fun place(
        session: SportbookSession,
        transactionId: String,
        currency: Currency,
        amount: Amount,
    ): Result<Bet> = runCatching {
        val externalId = ExternalBetId(transactionId)

        betRepository.findByExternalId(externalId)?.let { return@runCatching it }

        logger.info("Placing bet: player={} tx={} amount={}", session.playerId.value, transactionId, amount.value)

        walletPort.withdraw(session.playerId, placeTx(transactionId), currency, amount, Amount.ZERO)

        val bet = betRepository.save(
            Bet(
                externalId = externalId,
                playerId = session.playerId,
                session = session,
                currency = currency,
                betAmount = amount,
                type = BetType.SINGLE,
                selections = emptyList(),
            )
        )

        eventPublisher.publish(BetEvent(bet))

        bet
    }

    /** Rebinds the bet from the placement transaction id to the aggregator's bet id and fills the details. */
    suspend fun confirm(
        transactionId: String,
        externalId: String,
        type: BetType,
        selections: List<BetSelection>,
    ): Result<Bet> = runCatching {
        betRepository.findByExternalId(ExternalBetId(externalId))?.let { return@runCatching it }

        val bet = domainRequireNotNull(
            betRepository.findByExternalId(ExternalBetId(transactionId))
        ) { BetNotFoundException() }

        val confirmed = betRepository.save(
            bet.copy(
                externalId = ExternalBetId(externalId),
                type = type,
                selections = selections,
                updatedAt = InstantExt.now(),
            )
        )

        eventPublisher.publish(BetEvent(confirmed))

        confirmed
    }

    /**
     * Applies a (re-)settlement: moves the wallet by the reported delta, overwrites
     * status + winAmount. A clawback the balance cannot fully cover withdraws what is
     * available and reports the shortfall as Betting-managed [SettleBetResult.debt] —
     * the aggregator stores it and subtracts it from the bet's future payouts itself.
     *
     * [amount] moves against the REAL balance and nothing else. A sportbook stake is taken from
     * real money ([place] passes no bonus), so every payout returns there — including the parts
     * the aggregator itemises separately, such as an accumulator bonus. Splitting a payout onto
     * the bonus wallet also broke reversal: a clawback drains real, so a player kept the bonus
     * half of a win while the real half was taken back.
     */
    suspend fun settle(
        externalId: String,
        transactionId: String,
        currency: Currency,
        amount: Amount,
        credit: Boolean,
        won: Boolean,
    ): Result<SettleBetResult> = runCatching {
        val bet = domainRequireNotNull(
            betRepository.findByExternalId(ExternalBetId(externalId))
        ) { BetNotFoundException() }

        domainRequire(bet.currency == currency) { BetCurrencyMismatchException() }

        var debt = Amount.ZERO

        if (amount.value > 0) {
            if (credit) {
                walletPort.deposit(bet.playerId, settleTx(transactionId), currency, amount, Amount.ZERO)
            } else {
                debt = clawback(bet.playerId, transactionId, currency, required = amount)
            }
        }

        val settled = betRepository.save(
            bet.copy(
                status = if (won) BetStatus.WON else BetStatus.LOST,
                winAmount = if (won && credit) amount else Amount.ZERO,
                updatedAt = InstantExt.now(),
            )
        )

        eventPublisher.publish(BetEvent(settled))

        SettleBetResult(bet = settled, debt = debt)
    }

    /**
     * Withdraws up to [required] from the REAL balance, draining it if needed; returns the
     * uncovered rest as debt.
     *
     * Bonus money is out of reach here for the same reason it is out of reach everywhere else in
     * the sportbook: it never funded the stake, so it cannot be seized to reverse the payout. It
     * used to be drained on the shortfall path, which took a casino bonus to settle a sport debt.
     */
    private suspend fun clawback(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        required: Amount,
    ): Amount {
        val real = walletPort.findBalance(playerId, currency).realAmount

        if (real >= required) {
            walletPort.withdraw(playerId, settleTx(transactionId), currency, required, Amount.ZERO)
            return Amount.ZERO
        }

        if (real.value > 0) {
            walletPort.withdraw(playerId, settleTx(transactionId), currency, real, Amount.ZERO)
        }

        val debt = required - real
        logger.warn("Clawback not covered: player={} tx={} debt={}", playerId.value, transactionId, debt.value)
        return debt
    }

    /** Cancels a placement: refunds the reported amount and erases the bet. */
    suspend fun rollback(
        transactionId: String,
        currency: Currency,
        amount: Amount,
    ): Result<Unit> = runCatching {
        val bet = domainRequireNotNull(
            betRepository.findByExternalId(ExternalBetId(transactionId))
        ) { BetNotFoundException() }

        domainRequire(bet.currency == currency) { BetCurrencyMismatchException() }

        walletPort.deposit(bet.playerId, rollbackTx(transactionId), currency, amount, Amount.ZERO)

        betRepository.deleteById(bet.id)
    }

    private fun placeTx(transactionId: String) = "sportbook:place:$transactionId"

    private fun settleTx(transactionId: String) = "sportbook:settle:$transactionId"

    private fun rollbackTx(transactionId: String) = "sportbook:rollback:$transactionId"
}
