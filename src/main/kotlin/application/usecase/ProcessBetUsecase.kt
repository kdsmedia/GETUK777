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
     */
    suspend fun settle(
        externalId: String,
        transactionId: String,
        currency: Currency,
        realAmount: Amount,
        bonusAmount: Amount,
        credit: Boolean,
        won: Boolean,
    ): Result<SettleBetResult> = runCatching {
        val bet = domainRequireNotNull(
            betRepository.findByExternalId(ExternalBetId(externalId))
        ) { BetNotFoundException() }

        domainRequire(bet.currency == currency) { BetCurrencyMismatchException() }

        var debt = Amount.ZERO

        if (realAmount.value > 0 || bonusAmount.value > 0) {
            if (credit) {
                walletPort.deposit(bet.playerId, settleTx(transactionId), currency, realAmount, bonusAmount)
            } else {
                debt = clawback(bet.playerId, transactionId, currency, required = realAmount + bonusAmount)
            }
        }

        val settled = betRepository.save(
            bet.copy(
                status = if (won) BetStatus.WON else BetStatus.LOST,
                winAmount = if (won && credit) realAmount + bonusAmount else Amount.ZERO,
                updatedAt = InstantExt.now(),
            )
        )

        eventPublisher.publish(BetEvent(settled))

        SettleBetResult(bet = settled, debt = debt)
    }

    /** Withdraws up to [required], draining the balance if needed; returns the uncovered rest. */
    private suspend fun clawback(
        playerId: PlayerId,
        transactionId: String,
        currency: Currency,
        required: Amount,
    ): Amount {
        val balance = walletPort.findBalance(playerId, currency)

        if (balance.total >= required) {
            walletPort.withdraw(playerId, settleTx(transactionId), currency, required, Amount.ZERO)
            return Amount.ZERO
        }

        if (balance.total.value > 0) {
            walletPort.withdraw(playerId, settleTx(transactionId), currency, balance.realAmount, balance.bonusAmount)
        }

        val debt = required - balance.total
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
